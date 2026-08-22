# -*- coding: utf-8 -*-
"""UC-003 喷淋互斥 + 自动联动 E2E 验证（8081 临时实例）
覆盖：①初始全关闭 ②开启→拒绝重复开启 ③关闭→拒绝未开就关 ④状态联动
⑤自动联动超阈值开启 ⑥手动关闭联动喷淋 + 超标可再次自动开启 ⑦重启恢复（脚本2）
"""
import json, time, urllib.request, pymysql, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:8081'

def call(method, path, token=None, body=None):
    data = json.dumps(body).encode('utf-8') if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={'Content-Type': 'application/json'})
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return json.loads(r.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode('utf-8'))

def db():
    return pymysql.connect(host='localhost', user='root', password='123456', database='smart_site', charset='utf8mb4')

passed = []
def check(name, cond, extra=''):
    passed.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ((' | ' + str(extra)) if extra else ''))

# 登录
r = call('POST', '/api/auth/login', body={'username': 'admin', 'password': '123456'})
token = r.get('data', {}).get('token')
print('login:', r.get('code'), 'token:', bool(token))
assert token

# ① 初始全部关闭
s = call('GET', '/api/spray/status', token)
devices = s.get('data', [])
print('喷淋设备数:', len(devices))
for d in devices:
    print(' -', d['deviceName'], d['locationName'], 'spraying=', d['spraying'])
check('① 初始所有设备 spraying=false', all(not d['spraying'] for d in devices), [(d['deviceName'], d['spraying']) for d in devices])
loc_map = {d['locationName']: d for d in devices}
dong = loc_map['东侧']          # 联动设备 SPR-001, locationId=4

# ② 开启东侧 → 成功；重复开启 → 拒绝
r1 = call('POST', '/api/spray/manual', token, {'locationId': 4, 'action': 1, 'reason': 'E2E手动开启'})
check('② 首次开启东侧 成功', r1.get('code') == 0, r1)
r2 = call('POST', '/api/spray/manual', token, {'locationId': 4, 'action': 1, 'reason': '重复开启'})
check('② 重复开启被拒「该位置喷淋已开启，请先关闭」', r2.get('code') != 0 and '已开启' in str(r2.get('message')), r2)

# ③ 状态联动：开启后 status spraying=true
s = call('GET', '/api/spray/status', token)
dong_now = next(d for d in s['data'] if d['id'] == dong['id'])
check('③ 开启后 status spraying=true', dong_now['spraying'] is True, dong_now['spraying'])

# ④ 关闭东侧 → 成功；未开就关（二期先手动关再关）→ 拒绝
r3 = call('POST', '/api/spray/manual', token, {'locationId': 4, 'action': 2, 'reason': 'E2E手动关闭'})
check('④ 关闭东侧 成功', r3.get('code') == 0, r3)
r4 = call('POST', '/api/spray/manual', token, {'locationId': 4, 'action': 2, 'reason': '重复关闭'})
check('④ 重复关闭被拒「该位置喷淋未开启」', r4.get('code') != 0 and '未开启' in str(r4.get('message')), r4)
s = call('GET', '/api/spray/status', token)
dong_now = next(d for d in s['data'] if d['id'] == dong['id'])
check('④ 关闭后 status spraying=false', dong_now['spraying'] is False)

# ⑤ 自动联动：把东侧 PM2.5 环境值置为超标（>=75）→ 10s 内自动开启记录
conn = db(); cur = conn.cursor()
cur.execute("""UPDATE t_env_data SET index_value=140
               WHERE point_id=(SELECT id FROM t_env_monitor_point WHERE monitor_sub_type='PM2.5' LIMIT 1)
               ORDER BY collect_time DESC LIMIT 1""")
conn.commit()
print('已把最新 PM2.5 置为 140（超标），等待自动联动...')
time.sleep(12)
conn.close()
cur2 = db().cursor()
cur2.execute("SELECT point_id, device_id, trigger_type, action, reason, operator FROM t_spray_record WHERE trigger_type=3 ORDER BY id DESC LIMIT 1")
row = cur2.fetchone()
print('自动联动记录:', row)
check('⑤ 自动联动开启记录 triggerType=3, action=1, operator=system',
      row and row[2] == 3 and row[3] == 1 and row[5] == 'system', row)
check('⑤ reason 含实测值', row and '140' in str(row[4]) and '超标' in str(row[4]), row[4] if row else None)
s = call('GET', '/api/spray/status', token)
dong_now = next(d for d in s['data'] if d['id'] == dong['id'])
check('⑤ 自动联动后 status spraying=true', dong_now['spraying'] is True, dong_now['spraying'])

# ⑥ 联动喷淋中手动关闭 → 成功；仍超标 → 之后可再次自动开启
r5 = call('POST', '/api/spray/manual', token, {'locationId': 4, 'action': 2, 'reason': '联动期间手动关闭'})
check('⑥ 联动中手动关闭 成功', r5.get('code') == 0, r5)
time.sleep(12)  # 仍超标(140) → 再次自动开启
cur3 = db().cursor()
cur3.execute("SELECT trigger_type, action, reason FROM t_spray_record WHERE trigger_type=3 ORDER BY id DESC LIMIT 1")
row3 = cur3.fetchone()
print('手动关闭后最新联动记录:', row3)
check('⑥ 超标未恢复 → 可再次自动开启(action=1)', row3 and row3[0] == 3 and row3[1] == 1, row3)
s = call('GET', '/api/spray/status', token)
dong_now = next(d for d in s['data'] if d['id'] == dong['id'])
check('⑥ 再次自动开启后 spraying=true', dong_now['spraying'] is True)

# 恢复现场：把 PM2.5 置回正常(<50) 等待自动关闭（避免污染演示状态）
cur4 = db().cursor()
cur4.execute("""UPDATE t_env_data SET index_value=30
                WHERE point_id=(SELECT id FROM t_env_monitor_point WHERE monitor_sub_type='PM2.5' LIMIT 1)
                ORDER BY collect_time DESC LIMIT 1""")
db().commit()
print('\n===== 结果汇总 =====')
for name, ok in passed:
    print(('PASS' if ok else 'FAIL') + ' | ' + name)
print('总计:', len(passed), '通过:', sum(1 for _, ok in passed if ok))