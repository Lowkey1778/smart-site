# -*- coding: utf-8 -*-
"""实测 /api/coze/chat 接口（8081 新代码）：验证真实 Coze 响应耗时与返回"""
import json
import time
import urllib.request

body = json.dumps({"username": "admin", "password": "123456"}).encode()
req = urllib.request.Request('http://localhost:8081/api/auth/login', data=body, method='POST')
req.add_header('Content-Type', 'application/json')
with urllib.request.urlopen(req, timeout=10) as r:
    token = json.loads(r.read().decode())['data']['token']

t0 = time.time()
req = urllib.request.Request('http://localhost:8081/api/coze/chat',
                             data=json.dumps({"message": "今日安全态势如何？"}).encode(), method='POST')
req.add_header('Content-Type', 'application/json')
req.add_header('Authorization', f'Bearer {token}')
try:
    with urllib.request.urlopen(req, timeout=70) as r:
        j = json.loads(r.read().decode())
    print(f'耗时: {time.time()-t0:.1f}s')
    print(f'code={j.get("code")} source={j.get("data",{}).get("source")} engine={j.get("data",{}).get("engine")}')
    print(f'reply 前 120 字: {str(j.get("data",{}).get("reply"))[:120]}')
except Exception as e:
    print(f'调用失败: {e} 耗时 {time.time()-t0:.1f}s')
