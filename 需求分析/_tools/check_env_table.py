# -*- coding: utf-8 -*-
"""查询：环境设备类型id、t_env_monitor_point 表结构、t_device 最大id、ENV-002 监测点"""
import pymysql

conn = pymysql.connect(host='localhost', user='root', password='123456',
                       database='smart_site', charset='utf8mb4')
cur = conn.cursor()

print('=== t_device_type ===')
cur.execute("SELECT id, type_name FROM t_device_type")
for r in cur.fetchall():
    print(r)

print('\n=== t_env_monitor_point 建表 ===')
cur.execute("SHOW CREATE TABLE t_env_monitor_point")
print(cur.fetchone()[1])

print('\n=== t_device MAX(id) ===')
cur.execute("SELECT MAX(id) FROM t_device")
print(cur.fetchone())

print('\n=== ENV-002(南侧) 现有监测点 ===')
cur.execute("SELECT id, point_name, monitor_sub_type, device_id FROM t_env_monitor_point WHERE device_id=5")
for r in cur.fetchall():
    print(r)

conn.close()
