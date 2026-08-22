# -*- coding: utf-8 -*-
"""⑤⑥ 自动联动：置 PM2.5=140(超标) → 自动开启 → 手动关闭 → 仍超标再次自动开启"""
import json, time, urllib.request, pymysql, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

def call(base, method, path, token=None, body=None):
    data = json.dumps(body).encode('utf-8') if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method,
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

def set_pm25_latest(v):
    """只改最新一条 PM2.5 env_data（模拟器 5s 写入周期内捕获）"""
    conn = db(); cur = conn.cursor()
    cur.execute("""UPDATE t_env_data SET index_value=%s
                   WHERE point_id=(SELECT id FROM t_env_monitor_point WHERE monitor_sub_type='PM2.5' LIMIT 1)
                   ORDER BY collect_time DESC LIMIT 1""", (v,))
    conn.commit()
    print('最新 PM2.5 置为', v, '更新行数:', cur.rowcount)
    conn.close()

BASE = 'http://localhost:8081'
r = call(BASE, 'POST', '/api/auth/login', body={'username': 'admin', 'password': '123456'})
token = r.get('data', {}).get('token')

passed = []
def check(name, cond, extra=''):
    passed.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ((' | ' + str(extra)) if extra else ''))

# 恢复现场：先把当前喷淋关闭（东侧可能开着）
call(BASE, 'POST', '/api/spray/manual', token, {'locationId': 4, 'action': 2, 'reason': '测试前置关闭'})
set_pm25_latest(30)
time.sleep(8)  # 让 autoLink 确认正常

# ⑤ 超标 → 自动开启
set_pm25_latest(140)
time.sleep(6)  # autoLink 5s 周期，模拟器 5s 后才重写，窗口内捕获
conn = db(); cur = conn.cursor()
cur.execute("SELECT point_id, device_id, trigger_type, action, reason, operator FROM t_spray_record WHERE trigger_type=3 ORDER BY id DESC LIMIT 1")
row = cur.fetchone()
print('自动联动最新记录:', row)
check('⑤ 自动联动开启 triggerType=3 action=1 operator=system', row and row[2] == 3 and row[3] == 1 and row[5] == 'system', row)
check('⑤ reason 含 超标+实测值', row and '超标' in str(row[4]) and '140' in str(row[4]), row[4] if row else None)
s = call(BASE, 'GET', '/api/spray/status', token)
dong = next(d for d in s['data'] if d['locationName'] == '东侧')
check('⑤ 自动开启后东侧 spraying=true', dong['spraying'] is True)
conn.close()

# ⑥ 联动中手动关闭 → 成功；仍超标 → 再次自动开启
r1 = call(BASE, 'POST', '/api/spray/manual', token, {'locationId': 4, 'action': 2, 'reason': '联动期间手动关闭'})
check('⑥ 联动中手动关闭 成功', r1.get('code') == 0, r1)
set_pm25_latest(140)  # 手动关闭后数值仍超标
time.sleep(6)  # → 再次自动开启
conn = db(); cur = conn.cursor()
cur.execute("SELECT trigger_type, action, reason FROM t_spray_record WHERE trigger_type=3 ORDER BY id DESC LIMIT 1")
row2 = cur.fetchone()
print('手动关闭后最新联动记录:', row2)
check('⑥ 超标未恢复 → 再次自动开启 action=1', row2 and row2[0] == 3 and row2[1] == 1, row2)
s = call(BASE, 'GET', '/api/spray/status', token)
dong = next(d for d in s['data'] if d['locationName'] == '东侧')
check('⑥ 再次自动开启后 spraying=true', dong['spraying'] is True)
conn.close()

# 恢复现场：PM2.5 置正常 → 自动关闭（避免演示状态被污染）
set_pm25_latest(30)
time.sleep(8)
s = call(BASE, 'GET', '/api/spray/status', token)
dong = next(d for d in s['data'] if d['locationName'] == '东侧')
check('恢复现场：PM2.5 正常后自动关闭 spraying=false', dong['spraying'] is False)

print('\n===== 结果汇总 =====')
for name, ok in passed:
    print(('PASS' if ok else 'FAIL') + ' | ' + name)
print('总计:', len(passed), '通过:', sum(1 for _, ok in passed if ok))