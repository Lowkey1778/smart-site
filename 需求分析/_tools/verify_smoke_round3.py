# -*- coding: utf-8 -*-
"""smart-site 冒烟验证：喷淋6台 + Coze本地引擎 + 告警清空"""
import json
import urllib.request


def call(path, method='GET', body=None, token=None):
    req = urllib.request.Request('http://localhost:8080/api' + path, method=method)
    req.add_header('Content-Type', 'application/json; charset=utf-8')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    data = json.dumps(body).encode('utf-8') if body is not None else None
    with urllib.request.urlopen(req, data) as resp:
        return json.loads(resp.read().decode('utf-8'))


login = call('/auth/login', 'POST', {'username': 'admin', 'password': '123456'})
tok = login['data']['token']

print('=== spray/status (期望6台) ===')
spray = call('/spray/status', token=tok)['data']
for d in spray:
    print("  %s %s 位置=%s 在线=%s 喷淋中=%s 湿度=%s PM2.5=%s" % (
        d['deviceCode'], d['deviceName'], d['locationName'], d['status'],
        d['spraying'], d.get('humidity'), d.get('pm25')))
print('喷淋设备数量:', len(spray))

print('=== coze/chat (本地引擎, 资产问答) ===')
r = call('/coze/chat', 'POST', {'message': '有哪些设备资产？'}, tok)
print('REPLY:', r['data']['reply'][:150])
print('ENGINE:', r['data']['engine'][:40])

print('=== dashboard/stats (告警已清空) ===')
st = call('/dashboard/stats', token=tok)['data']
print('stats:', json.dumps(st, ensure_ascii=False))
