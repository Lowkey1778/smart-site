# -*- coding: utf-8 -*-
"""查询 coze 配置 + 喷淋/环境设备与监测点现状"""
import pymysql

conn = pymysql.connect(host='localhost', user='root', password='123456',
                       database='smart_site', charset='utf8mb4')
cur = conn.cursor()

print('=== t_sys_config (coze) ===')
cur.execute("SELECT config_key, config_value FROM t_sys_config WHERE config_key LIKE 'coze%'")
for r in cur.fetchall():
    k, v = r
    if 'token' in k and v:
        v = v[:6] + '...' + v[-4:] if len(v) > 12 else '****'
    print(f'{k} = {v}')

print('\n=== 喷淋设备 (type 含喷淋) ===')
cur.execute("""SELECT d.id, d.device_code, d.device_name, d.location_id, l.location_name, d.status
               FROM t_device d LEFT JOIN t_device_location l ON d.location_id = l.id
               WHERE d.type_id IN (SELECT id FROM t_device_type WHERE type_name LIKE '%喷淋%')""")
for r in cur.fetchall():
    print(r)

print('\n=== 环境监测设备 ===')
cur.execute("""SELECT d.id, d.device_code, d.device_name, d.location_id, l.location_name
               FROM t_device d LEFT JOIN t_device_location l ON d.location_id = l.id
               WHERE d.type_id IN (SELECT id FROM t_device_type WHERE type_name LIKE '%环境%')""")
for r in cur.fetchall():
    print(r)

print('\n=== 环境监测点 ===')
cur.execute("""SELECT p.id, p.point_name, p.monitor_sub_type, p.device_id, p.unit, p.warn_max, p.warn_min
               FROM t_env_monitor_point p""")
for r in cur.fetchall():
    print(r)

print('\n=== 喷淋联动配置 (t_device_monitor_point 含 spray 字段) ===')
cur.execute("""SELECT p.id, p.point_name, p.monitor_sub_type, p.device_id, p.spray_enabled,
               p.spray_on_threshold, p.spray_off_threshold, p.spray_device_id
               FROM t_device_monitor_point p WHERE p.spray_enabled IS NOT NULL OR p.spray_device_id IS NOT NULL""")
for r in cur.fetchall():
    print(r)

print('\n=== t_realtime_data 最近 env 相关 param_code ===')
cur.execute("""SELECT param_code, COUNT(*), MIN(param_value), MAX(param_value)
               FROM t_realtime_data WHERE param_code IN ('PM2.5','PM10','湿度','temperature','humidity')
               GROUP BY param_code""")
for r in cur.fetchall():
    print(r)

conn.close()
