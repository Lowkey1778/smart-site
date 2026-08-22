# -*- coding: utf-8 -*-
"""验证后端 /api/spray/status 返回（8080 用户实例）+ 新监测点数据生成情况"""
import json
import urllib.request
import pymysql

# 1. 登录拿 token
def login():
    body = json.dumps({"username": "admin", "password": "123456"}).encode()
    req = urllib.request.Request('http://localhost:8081/api/auth/login', data=body, method='POST')
    req.add_header('Content-Type', 'application/json')
    with urllib.request.urlopen(req, timeout=10) as r:
        j = json.loads(r.read().decode())
    return j['data']['token']

token = login()
print('登录 OK')

# 2. 调 /api/spray/status
req = urllib.request.Request('http://localhost:8081/api/spray/status')
req.add_header('Authorization', f'Bearer {token}')
with urllib.request.urlopen(req, timeout=10) as r:
    j = json.loads(r.read().decode())
print(f'code={j.get("code")} 共 {len(j.get("data") or [])} 台喷淋设备')
for d in j['data']:
    print(f"  {d['deviceName']}({d['locationName']}): 湿度={d.get('humidity')} {d.get('humidityUnit')} | "
          f"PM2.5={d.get('pm25')} {d.get('pm25Unit')} | env={d.get('envDeviceName')} | spraying={d.get('spraying')}")

# 3. 新监测点数据
conn = pymysql.connect(host='localhost', user='root', password='123456',
                       database='smart_site', charset='utf8mb4')
cur = conn.cursor()
cur.execute("""SELECT p.point_code, COUNT(d.id), MAX(d.collect_time)
               FROM t_env_monitor_point p LEFT JOIN t_env_data d ON d.point_id = p.id
               WHERE p.point_code LIKE 'ENV-PM25-0%' OR p.point_code LIKE 'ENV-HUMI-0%'
               GROUP BY p.point_code ORDER BY p.point_code""")
print('\n=== 各 PM2.5/湿度监测点数据量 ===')
for r in cur.fetchall():
    print(f'  {r[0]}: {r[1]} 条, 最新={r[2]}')

cur.execute("SELECT id, device_code, type_id, location_id FROM t_device WHERE device_code LIKE 'ENV-%'")
print('\n=== 环境设备 type_id ===')
for r in cur.fetchall():
    print(f'  id={r[0]} {r[1]} type_id={r[2]} location_id={r[3]}')
conn.close()
