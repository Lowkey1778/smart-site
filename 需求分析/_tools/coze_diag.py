# -*- coding: utf-8 -*-
"""Coze v3 API 直连诊断（技能 coze-bot-integration 标准模式）：
从 t_sys_config 读 token/bot_id（不打印完整 token），直连 api.coze.cn：
1) 创建对话（v3/chat）——验证 token 有效性 + bot 是否发布到 Agent as API
2) 轮询 retrieve——验证状态流转
3) 拉消息 message/list——验证端点
"""
import json
import time
import urllib.request
import urllib.error
import pymysql

conn = pymysql.connect(host='localhost', user='root', password='123456',
                       database='smart_site', charset='utf8mb4')
cur = conn.cursor()
cur.execute("SELECT config_key, config_value FROM t_sys_config WHERE config_key LIKE 'coze%'")
cfg = {k: v for k, v in cur.fetchall()}
conn.close()

token = (cfg.get('coze.api_token') or '').strip()
bot_id = (cfg.get('coze.bot_id') or '').strip()
base = (cfg.get('coze.base_url') or 'https://api.coze.cn').strip()
print(f'token 配置 = {bool(token)} (前6位 {token[:6] if token else ""}...)')
print(f'bot_id = {bot_id}')
print(f'base_url = {base}')
print('=' * 60)

def call(method, path, body=None):
    url = base + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header('Authorization', f'Bearer {token}')
    req.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {'raw': str(e)}
    except Exception as e:
        return None, {'error': str(e)}

# 1. 创建对话
print('\n[1] POST /v3/chat (创建对话)')
body = {
    'bot_id': bot_id,
    'user_id': 'diag_user_1',
    'stream': False,
    'auto_save_history': True,
    'additional_messages': [{'role': 'user', 'content_type': 'text', 'content': '你好，请自我介绍一下'}]
}
status, j = call('POST', '/v3/chat', body)
print(f'HTTP {status}')
print(json.dumps(j, ensure_ascii=False)[:800])

if j.get('code') == 0:
    cid = j['data']['conversation_id']
    chatid = j['data']['id']
    # 2. 轮询
    print('\n[2] GET /v3/chat/retrieve (轮询状态)')
    for i in range(15):
        time.sleep(1)
        s2, j2 = call('GET', f'/v3/chat/retrieve?conversation_id={cid}&chat_id={chatid}')
        st = j2.get('data', {}).get('status') if isinstance(j2, dict) else None
        print(f'  {i+1}s: HTTP {s2} status={st}')
        if st in ('completed', 'failed', 'requires_action'):
            break
    # 3. 拉消息
    print('\n[3] GET /v3/chat/message/list (拉回答)')
    s3, j3 = call('GET', f'/v3/chat/message/list?conversation_id={cid}&chat_id={chatid}')
    print(f'HTTP {s3}')
    items = j3.get('data') if isinstance(j3, dict) else None
    if isinstance(items, list):
        for it in items:
            print(f"  role={it.get('role')} type={it.get('type')} content={str(it.get('content'))[:200]}")
    else:
        print(json.dumps(j3, ensure_ascii=False)[:800])
