# -*- coding: utf-8 -*-
"""验证：新环境监测点是否已有模拟数据 + /spray/status 数据源是否就绪"""
import pymysql

conn = pymysql.connect(host='localhost', user='root', password='123456',
                       database='smart_site', charset='utf8mb4')
cur = conn.cursor()

print('=== 新环境设备 + 监测点 ===')
cur.execute("""SELECT p.id, p.point_code, p.monitor_sub_type, p.device_id, d.device_code, l.location_name
               FROM t_env_monitor_point p
               JOIN t_device d ON p.device_id = d.id
               LEFT JOIN t_device_location l ON d.location_id = l.id
               ORDER BY p.id""")
for r in cur.fetchall():
    print(r)

print('\n=== 新监测点最新 env_data（模拟器是否已生成） ===')
cur.execute("""SELECT p.point_code, d.index_value, d.collect_time
               FROM t_env_data d JOIN t_env_monitor_point p ON d.point_id = p.id
               WHERE p.point_code LIKE 'ENV-PM25-0%' OR p.point_code LIKE 'ENV-HUMI-0%'
               ORDER BY d.collect_time DESC LIMIT 12""")
for r in cur.fetchall():
    print(r)

print('\n=== /spray/status 数据源核对：每个位置湿度/PM2.5 点 ===')
cur.execute("""SELECT l.id, l.location_name,
               SUM(CASE WHEN p.monitor_sub_type='湿度' THEN 1 ELSE 0 END) AS hum_cnt,
               SUM(CASE WHEN p.monitor_sub_type='PM2.5' THEN 1 ELSE 0 END) AS pm_cnt
               FROM t_device_location l
               LEFT JOIN t_device d ON d.location_id = l.id AND d.type_id=4
               LEFT JOIN t_env_monitor_point p ON p.device_id = d.id
               GROUP BY l.id, l.location_name ORDER BY l.id""")
for r in cur.fetchall():
    print(f'loc{r[0]} {r[1]}: 湿度点={r[2]} PM2.5点={r[3]}')

conn.close()
