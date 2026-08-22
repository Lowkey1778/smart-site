# -*- coding: utf-8 -*-
"""受控验证：清空记录 + PM2.5=30 后重启后端 → ①初始全关闭 + ②首次手动开启成功 + ④未开就关被拒"""
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

def wait_ready(base, tries=60):
    for _ in range(tries):
        try:
            call(base, 'POST', '/api/auth/login', body={'username': 'x', 'password': 'x'})
            return True
        except Exception:
            time.sleep(4)
    return False

BASE = 'http://localhost:8081'
if not wait_ready(BASE):
    print('BACKEND NOT READY')
    sys.exit(1)
print('后端就绪')

r = call(BASE, 'POST', '/api/auth/login', body={'username': 'admin', 'password': '123456'})
token = r.get('data', {}).get('token')

passed = []
def check(name, cond, extra=''):
    passed.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ((' | ' + str(extra)) if extra else ''))

# ① 初始全部关闭（记录已清空，PM2.5=30 正常范围）
s = call(BASE, 'GET', '/api/spray/status', token)
st = {d['locationName']: d['spraying'] for d in s.get('data', [])}
print('status:', st)
check('① 初始所有设备 spraying=false', all(v is False for v in st.values()), st)

# ② 首次手动开启东侧 → 成功
r1 = call(BASE, 'POST', '/api/spray/manual', token, {'locationId': 4, 'action': 1, 'reason': '受控首次开启'})
check('② 首次手动开启东侧 成功', r1.get('code') == 0, r1)
# 开启后 status=true
s = call(BASE, 'GET', '/api/spray/status', token)
dong = next(d for d in s['data'] if d['locationName'] == '东侧')
check('② 开启后东侧 spraying=true', dong['spraying'] is True, dong['spraying'])
# ③ 喷淋中重复开启被拒
r2 = call(BASE, 'POST', '/api/spray/manual', token, {'locationId': 4, 'action': 1, 'reason': '重复开启'})
check('③ 喷淋中重复开启被拒', r2.get('code') != 0 and '已开启' in str(r2.get('message')), r2)
# ④ 关闭成功 + 未开就关被拒
r3 = call(BASE, 'POST', '/api/spray/manual', token, {'locationId': 4, 'action': 2, 'reason': '受控关闭'})
check('④ 关闭东侧 成功', r3.get('code') == 0, r3)
r4 = call(BASE, 'POST', '/api/spray/manual', token, {'locationId': 4, 'action': 2, 'reason': '重复关闭'})
check('④ 关闭后再次关闭被拒「未开启」', r4.get('code') != 0 and '未开启' in str(r4.get('message')), r4)

print('\n===== 结果汇总 =====')
for name, ok in passed:
    print(('PASS' if ok else 'FAIL') + ' | ' + name)
print('总计:', len(passed), '通过:', sum(1 for _, ok in passed if ok))