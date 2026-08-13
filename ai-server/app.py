# -*- coding: utf-8 -*-
"""
建筑安全智能监控平台 - AI 推理服务
技术栈: Flask + Ultralytics YOLOv8n（真实视觉推理）
接口:
  POST /api/ai/detect  图片危险行为检测 (multipart, 字段名 image)
  GET  /api/ai/health  健康检查

检测引擎（T-18 / RQ-24，真实视觉模型替换模拟推理引擎）:
  RealEngine - YOLOv8n(COCO) 真实目标检测 + OpenCV 图像分析后处理:
    * 安全帽未佩戴: 检测到 person 后, 分析头部区域 HSV 颜色特征判断是否戴帽
    * 安全服未穿  : 分析躯干区域是否存在高可见性安全服颜色(橙/荧光黄)
    * 现场吸烟    : 嘴部区域亮斑检测
    * 明火        : 全图火焰 HSV 特征分析
  模型加载失败或 AI_ENGINE=sim 时自动降级为 SimEngine（演示兜底）。
"""
import os
import random
import time

from flask import Flask, jsonify, request

app = Flask(__name__)

# 危险行为类别 (与《页面功能清单》七、AI智能识别 对应)
LABELS_ZH = {
    "helmet": "安全帽未佩戴",
    "vest":   "安全服未穿",
    "smoke":  "现场吸烟",
    "fire":   "明火",
}

MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "yolov8n.pt")


class SimEngine:
    """模拟推理引擎（兜底）：40% 概率检测到 1-2 个危险行为"""

    def detect(self, image_path: str) -> list:
        time.sleep(0.3)
        results = []
        if random.random() < 0.4:
            n = random.randint(1, 2)
            labels = random.sample(list(LABELS_ZH.keys()), n)
            for label in labels:
                results.append({
                    "label": label,
                    "label_zh": LABELS_ZH[label],
                    "confidence": round(random.uniform(0.65, 0.95), 3),
                    "bbox": [random.randint(50, 300), random.randint(30, 200),
                             random.randint(80, 200), random.randint(80, 220)],
                })
        return results


class RealEngine:
    """真实视觉推理引擎：YOLOv8n 人员检测 + OpenCV 图像特征分析"""

    def __init__(self, model_path):
        from ultralytics import YOLO
        import cv2
        self.cv2 = cv2
        self.model = YOLO(model_path)

    def detect(self, image_path: str) -> list:
        cv2 = self.cv2
        img = cv2.imread(image_path)
        if img is None:
            return []
        results = []
        try:
            preds = self.model(image_path, conf=0.30, verbose=False)
        except Exception:
            return []

        persons = []
        for r in preds:
            if r.boxes is None:
                continue
            for box in r.boxes:
                cls = int(box.cls[0])
                conf = float(box.conf[0])
                if cls == 0:  # COCO person
                    x1, y1, x2, y2 = (int(v) for v in box.xyxy[0].tolist())
                    persons.append((x1, y1, x2, y2, conf))

        h, w = img.shape[:2]

        # 1. 全图明火检测（火焰 HSV 特征：黄橙红、高饱和、高亮；排除人员区域误判）
        fire_box, fire_conf = self._detect_fire(img, persons)
        if fire_box:
            results.append({
                "label": "fire", "label_zh": LABELS_ZH["fire"],
                "confidence": round(fire_conf, 3), "bbox": fire_box,
            })

        # 2. 逐人分析：安全帽 / 安全服 / 吸烟
        for (x1, y1, x2, y2, conf) in persons:
            # 边界裁剪
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(w, x2), min(h, y2)
            if x2 - x1 < 12 or y2 - y1 < 12:
                continue
            body_h = y2 - y1
            head_top = y1
            head_bottom = y1 + int(body_h * 0.22)
            head = img[head_top:head_bottom, x1:x2]

            # 2.1 安全帽：头部区域是否存在帽色（黄/红/蓝/白/橙）
            if head.size > 0:
                has_helmet, helmet_conf = self._has_helmet(head)
                if not has_helmet:
                    results.append({
                        "label": "helmet", "label_zh": LABELS_ZH["helmet"],
                        "confidence": round(max(conf, 0.5), 3),
                        "bbox": [x1, head_top, x2 - x1, head_bottom - head_top],
                    })

            # 2.2 安全服：躯干区域是否有高可见性颜色（橙/荧光黄/荧光绿）
            torso = img[int(y1 + body_h * 0.25):int(y1 + body_h * 0.8), x1:x2]
            if torso.size > 0:
                has_vest, vest_conf = self._has_vest(torso)
                if not has_vest:
                    results.append({
                        "label": "vest", "label_zh": LABELS_ZH["vest"],
                        "confidence": round(max(conf - 0.1, 0.45), 3),
                        "bbox": [x1, int(y1 + body_h * 0.25), x2 - x1, int(body_h * 0.55)],
                    })

            # 2.3 吸烟：嘴部区域（头部下方）红/亮斑检测
            mouth = img[int(y1 + body_h * 0.18):int(y1 + body_h * 0.30), x1:x2]
            if mouth.size > 0:
                has_smoke, smoke_conf = self._detect_smoke(mouth)
                if has_smoke:
                    results.append({
                        "label": "smoke", "label_zh": LABELS_ZH["smoke"],
                        "confidence": round(smoke_conf, 3),
                        "bbox": [x1, int(y1 + body_h * 0.18), x2 - x1, int(body_h * 0.12)],
                    })

        # 去重（同标签只保留置信度最高的一条）
        dedup = {}
        for r in results:
            k = r["label"]
            if k not in dedup or r["confidence"] > dedup[k]["confidence"]:
                dedup[k] = r
        return list(dedup.values())

    # ---------- 图像特征分析 ----------

    def _has_helmet(self, head):
        """头部区域是否存在安全帽颜色（黄/橙/红/蓝/白）"""
        cv2 = self.cv2
        hsv = cv2.cvtColor(head, cv2.COLOR_BGR2HSV)
        masks = [
            cv2.inRange(hsv, (15, 80, 120), (35, 255, 255)),    # 黄
            cv2.inRange(hsv, (0, 90, 100), (12, 255, 255)),     # 红橙
            cv2.inRange(hsv, (95, 90, 90), (130, 255, 255)),    # 蓝
            cv2.inRange(hsv, (0, 0, 180), (180, 40, 255)),      # 白
        ]
        total = head.shape[0] * head.shape[1]
        for m in masks:
            ratio = cv2.countNonZero(m) / max(total, 1)
            if ratio > 0.30:
                return True, round(min(ratio + 0.4, 0.95), 3)
        return False, 0.0

    def _has_vest(self, torso):
        """躯干区域是否存在高可见性安全服颜色（橙/荧光黄）"""
        cv2 = self.cv2
        hsv = cv2.cvtColor(torso, cv2.COLOR_BGR2HSV)
        masks = [
            cv2.inRange(hsv, (10, 120, 120), (28, 255, 255)),   # 橙色
            cv2.inRange(hsv, (28, 140, 140), (45, 255, 255)),   # 荧光黄
        ]
        total = torso.shape[0] * torso.shape[1]
        for m in masks:
            ratio = cv2.countNonZero(m) / max(total, 1)
            if ratio > 0.18:
                return True, round(min(ratio + 0.35, 0.95), 3)
        return False, 0.0

    def _detect_smoke(self, mouth):
        """嘴部区域：检测烟头/火点亮斑（高亮橙色小区域）"""
        cv2 = self.cv2
        if mouth.shape[0] < 4 or mouth.shape[1] < 4:
            return False, 0.0
        hsv = cv2.cvtColor(mouth, cv2.COLOR_BGR2HSV)
        mask = cv2.inRange(hsv, (0, 80, 150), (30, 255, 255))
        ratio = cv2.countNonZero(mask) / (mouth.shape[0] * mouth.shape[1])
        if ratio > 0.05:
            return True, round(min(ratio * 6 + 0.5, 0.9), 3)
        return False, 0.0

    def _detect_fire(self, img, persons=None):
        """全图明火检测：火焰 HSV 特征（黄橙红 + 高饱和 + 高亮）
        排除与人员检测框高度重叠的区域（安全帽/安全服橙色易误判）"""
        cv2 = self.cv2
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        mask = cv2.inRange(hsv, (0, 100, 120), (40, 255, 255))
        cnts, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not cnts:
            return None, 0.0
        img_area = img.shape[0] * img.shape[1]
        best = None
        best_ratio = 0.0
        for c in cnts:
            area = cv2.contourArea(c)
            ratio = area / max(img_area, 1)
            x, y, bw, bh = cv2.boundingRect(c)
            # 与人员框重叠面积占比 > 35% 的区域视为人员着装，跳过
            if persons:
                overlap = False
                for (px1, py1, px2, py2, _) in persons:
                    ox1, oy1 = max(x, px1), max(y, py1)
                    ox2, oy2 = min(x + bw, px2), min(y + bh, py2)
                    if ox2 > ox1 and oy2 > oy1:
                        inter = (ox2 - ox1) * (oy2 - oy1)
                        if inter / max(area, 1) > 0.35:
                            overlap = True
                            break
                if overlap:
                    continue
            if ratio > best_ratio:
                best_ratio = ratio
                best = (x, y, bw, bh)
        if best and best_ratio > 0.03:  # 火焰区域占比超过 3%
            return list(best), round(min(best_ratio * 8 + 0.45, 0.95), 3)
        return None, 0.0


# 引擎选择：AI_ENGINE=sim 强制模拟；默认 real（模型加载失败自动降级 sim）
engine_name = os.environ.get("AI_ENGINE", "real")
engine = None
if engine_name == "real":
    try:
        engine = RealEngine(MODEL_PATH)
        print("[AI] 真实视觉引擎已加载: YOLOv8n")
    except Exception as e:
        print(f"[AI] YOLO 模型加载失败，降级为模拟引擎: {e}")
        engine = SimEngine()
        engine_name = "sim"
else:
    engine = SimEngine()
    print("[AI] 模拟推理引擎（AI_ENGINE=sim）")


@app.get("/api/ai/health")
def health():
    return jsonify({"code": 0, "message": "ok", "engine": engine_name})


@app.post("/api/ai/detect")
def detect():
    if "image" not in request.files:
        return jsonify({"code": 400, "message": "缺少 image 文件"}), 400
    f = request.files["image"]
    tmp = os.path.join(app.root_path, "tmp", f"{int(time.time() * 1000)}.jpg")
    os.makedirs(os.path.dirname(tmp), exist_ok=True)
    f.save(tmp)
    try:
        results = engine.detect(tmp)
        return jsonify({"code": 0, "data": {"results": results, "count": len(results)}})
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
