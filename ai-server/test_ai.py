# -*- coding: utf-8 -*-
"""真实引擎验证：真实人像图 + 火焰图"""
import requests
import cv2
import numpy as np

# 1. 真实人像图（bus.jpg 含 4 人，ultralytics 官方示例）
with open('tmp_bus.jpg', 'rb') as f:
    r = requests.post('http://127.0.0.1:5000/api/ai/detect',
                      files={'image': ('bus.jpg', f, 'image/jpeg')}, timeout=120)
res = r.json()['data']['results']
print(f'bus.jpg 真实人像: 检出 {len(res)} 项')
for x in res:
    print(f"  - {x['label_zh']} conf={x['confidence']} bbox={x['bbox']}")

# 2. 火焰图（深色背景 + 大面积橙色亮斑，无人 → 应检出明火）
img = np.full((360, 360, 3), 30, dtype=np.uint8)
cv2.ellipse(img, (180, 300), (70, 40), 0, 180, 360, (0, 120, 255), -1)
cv2.ellipse(img, (180, 285), (45, 25), 0, 180, 360, (0, 180, 255), -1)
cv2.ellipse(img, (180, 272), (25, 14), 0, 180, 360, (0, 230, 255), -1)
cv2.imwrite('tmp_fire.jpg', img)
with open('tmp_fire.jpg', 'rb') as f:
    r2 = requests.post('http://127.0.0.1:5000/api/ai/detect',
                       files={'image': ('fire.jpg', f, 'image/jpeg')}, timeout=120)
res2 = r2.json()['data']['results']
print(f'火焰图(无人): 检出 {len(res2)} 项')
for x in res2:
    print(f"  - {x['label_zh']} conf={x['confidence']}")
