# -*- coding: utf-8 -*-
"""验证 8081 临时实例 /api/env/points 返回位置字段"""
import json, urllib.request, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:8081'

def post(path, body, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode('utf-8'),
                                 headers={'Content-Type': 'application/json'})
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode('utf-8'))

def get(path, token):
    req = urllib.request.Request(BASE + path, headers={'Authorization': 'Bearer ' + token})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode('utf-8'))

# 登录
r = post('/api/auth/login', {'username': 'admin', 'password': '123456'})
token = r.get('data', {}).get('token')
print('login code:', r.get('code'), 'token:', bool(token))
if not token:
    print('LOGIN FAIL:', json.dumps(r, ensure_ascii=False)[:500])
    sys.exit(1)

# 拉环境监测点
pts = get('/api/env/points', token)
print('points count:', len(pts.get('data', [])))
for p in pts.get('data', [])[:20]:
    print(p.get('pointCode'), '|', p.get('monitorSubType'), '|', p.get('locationName'), '|', p.get('deviceName'))