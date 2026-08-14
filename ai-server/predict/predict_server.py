# -*- coding: utf-8 -*-
"""
塔吊安全状态预测服务（T-33 / RQ-17）
Flask 服务 :5001，加载 CNN+LSTM+Attention 模型，提供预测接口。

接口：
  POST /api/predict/crane  塔吊安全状态预测
  GET  /api/predict/health 健康检查

启动：.venv\\Scripts\\python.exe predict\\predict_server.py
"""
import os
import pickle

import numpy as np
import torch
import torch.nn as nn
from flask import Flask, jsonify, request

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(HERE, "crane_model.pt")
SCALER_PATH = os.path.join(HERE, "scaler.pkl")
T = 60

app = Flask(__name__)


class CranePredictNet(nn.Module):
    """CNN + LSTM + Attention（与训练脚本结构一致）"""

    def __init__(self, in_dim=5, hidden=64, num_layers=2, out_dim=2):
        super().__init__()
        self.conv1 = nn.Conv1d(in_dim, 32, kernel_size=3, padding=1)
        self.relu = nn.ReLU()
        self.lstm = nn.LSTM(32, hidden, num_layers=num_layers,
                            batch_first=True, dropout=0.2)
        self.attn = nn.Linear(hidden, 1)
        self.fc = nn.Sequential(
            nn.Linear(hidden, 32),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(32, out_dim),
        )

    def forward(self, x):
        h = self.conv1(x.transpose(1, 2)).transpose(1, 2)
        h = self.relu(h)
        out, _ = self.lstm(h)
        attn_w = torch.softmax(self.attn(out), dim=1)
        ctx = (out * attn_w).sum(dim=1)
        return self.fc(ctx)


# ---------- 模型加载 ----------
model = None
load_scaler = None
feature_scaler = None
model_info = {}


def load_model():
    global model, load_scaler, feature_scaler, model_info
    if not (os.path.exists(MODEL_PATH) and os.path.exists(SCALER_PATH)):
        return False
    ckpt = torch.load(MODEL_PATH, map_location="cpu", weights_only=False)
    cfg = ckpt["config"]
    model = CranePredictNet(**cfg)
    model.load_state_dict(ckpt["state_dict"])
    model.eval()
    with open(SCALER_PATH, "rb") as f:
        sc = pickle.load(f)
    feature_scaler = sc["feature_scaler"]
    load_scaler = sc["load_scaler"]
    model_info = {
        "t": ckpt.get("t", T),
        "features": ckpt.get("features", []),
        "load_warn": ckpt.get("load_warn"),
        "wind_warn": ckpt.get("wind_warn"),
    }
    return True


MODEL_READY = load_model()


@app.get("/api/predict/health")
def health():
    return jsonify({"code": 0, "message": "ok",
                    "model_ready": MODEL_READY,
                    "model": model_info or None})


@app.post("/api/predict/crane")
def predict_crane():
    if not MODEL_READY:
        return jsonify({"code": 500, "message": "预测模型未就绪，请先运行 train_crane_model.py 训练",
                        "data": None}), 500
    body = request.get_json(force=True, silent=True) or {}
    history = body.get("history")
    device_code = body.get("deviceCode", "")
    if not history or len(history) < T:
        return jsonify({"code": 400,
                        "message": f"历史数据不足，需要至少 {T} 个时间点（当前 {len(history or [])}）",
                        "data": None}), 400

    # history: [[load, radius, wind_speed, height, angle], ...] 时间升序，取最后 T 个
    window = np.array(history[-T:], dtype=np.float32)
    if window.shape != (T, 5):
        return jsonify({"code": 400, "message": "特征维度应为 5 列 [load,radius,wind_speed,height,angle]",
                        "data": None}), 400

    # 归一化
    norm = feature_scaler.transform(window.reshape(-1, 5)).reshape(1, T, 5)
    with torch.no_grad():
        out = model(torch.tensor(norm))
        pred_load_norm = out[0, 0].item()
        risk_logit = out[0, 1].item()

    pred_load = float(load_scaler.inverse_transform([[pred_load_norm]])[0][0])
    risk_prob = float(1.0 / (1.0 + np.exp(-risk_logit)))

    load_warn = model_info.get("load_warn", 7.2)
    wind_warn = model_info.get("wind_warn", 12.0)
    cur_load = float(window[-1][0])
    cur_wind = float(window[-1][2])

    # 风险分级
    if risk_prob >= 0.7 or cur_load > load_warn or cur_wind > wind_warn:
        level, advice = "高风险", "预测吊重/风速接近或超过预警阈值，建议立即降低吊重、停止吊装作业并检查设备状态"
    elif risk_prob >= 0.4:
        level, advice = "中风险", "运行参数呈上升趋势，建议加密监测频次，提前安排安全检查"
    else:
        level, advice = "低风险", "运行状态平稳，可按计划继续作业，保持例行监测"

    return jsonify({"code": 0, "data": {
        "deviceCode": device_code,
        "predLoad": round(pred_load, 2),
        "curLoad": round(cur_load, 2),
        "curWindSpeed": round(cur_wind, 2),
        "riskProb": round(risk_prob, 3),
        "level": level,
        "advice": advice,
        "model": "CNN+LSTM+Attention",
    }})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001)
