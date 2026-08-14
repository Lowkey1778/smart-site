# -*- coding: utf-8 -*-
"""
塔吊安全状态预测模型训练（T-33 / RQ-17）
模型：CNN + LSTM + Attention 时序预测
输入：最近 T=60 个时间步 × 5 个特征 [吊重, 幅度, 风速, 高度, 回转角度]
输出：下一时刻吊重预测 + 安全风险概率（基于吊重/风速阈值计算标签）

数据来源：smart_site.t_realtime_data 中塔吊设备(device_id=1,2)的历史实时数据
产物：crane_model.pt（模型权重）、scaler.pkl（归一化参数）

用法：.venv\\Scripts\\python.exe predict\\train_crane_model.py
"""
import os
import sys
import random

import numpy as np
import pymysql
import torch
import torch.nn as nn
from sklearn.preprocessing import StandardScaler
import pickle

# ---------- 配置 ----------
DB = dict(host="127.0.0.1", port=3306, user="root", password="123456",
          database="smart_site", charset="utf8mb4")
DEVICE_IDS = [1, 2]          # 塔吊设备
FEATURES = ["load", "radius", "wind_speed", "height", "angle"]
T = 60                       # 输入时间步（60 * 5s = 5 分钟窗口）
LOAD_WARN = 7.2              # 吊重预警阈值(t)（与 MockDataScheduler 一致）
WIND_WARN = 12.0             # 风速预警阈值(m/s)
EPOCHS = 60
BATCH_SIZE = 64
LR = 1e-3
SEED = 42
HERE = os.path.dirname(os.path.abspath(__file__))

random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)


def load_series(device_id, limit=20000):
    """读取设备历史实时数据，按时间排序，聚合为 (N, F) 特征矩阵"""
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT param_code, param_value, collect_time FROM t_realtime_data "
                "WHERE device_id=%s AND param_code IN (%s,%s,%s,%s,%s) "
                "ORDER BY collect_time DESC LIMIT %s",
                (device_id, *FEATURES, limit))
            rows = cur.fetchall()
    finally:
        conn.close()

    # 按时间升序聚合：每条记录 (time, code, value)
    by_time = {}
    for code, value, t in rows:
        by_time.setdefault(t, {})[code] = float(value)

    seq = []
    for t in sorted(by_time.keys()):
        row = by_time[t]
        if all(k in row for k in FEATURES):
            seq.append([row[k] for k in FEATURES])
    return np.array(seq, dtype=np.float32)


def make_samples(seq, load_warn=LOAD_WARN, wind_warn=WIND_WARN):
    """滑窗生成样本：X=(T,F)，y=[下一时刻吊重, 风险标签]"""
    X, y = [], []
    for i in range(T, len(seq) - 1):
        window = seq[i - T:i]
        nxt = seq[i + 1]
        load = nxt[0]
        wind = nxt[2]
        risk = 1.0 if (load > load_warn or wind > wind_warn) else 0.0
        X.append(window)
        y.append([load, risk])
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.float32)


class CranePredictNet(nn.Module):
    """CNN + LSTM + Attention"""

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
        # x: (B, T, F) -> conv1d 需要 (B, F, T)
        h = self.conv1(x.transpose(1, 2)).transpose(1, 2)  # (B, T, 32)
        h = self.relu(h)
        out, _ = self.lstm(h)                               # (B, T, hidden)
        attn_w = torch.softmax(self.attn(out), dim=1)       # (B, T, 1)
        ctx = (out * attn_w).sum(dim=1)                     # (B, hidden)
        return self.fc(ctx)


def main():
    print("=== 塔吊安全状态预测模型训练（CNN+LSTM+Attention）===")
    all_x, all_y = [], []
    for did in DEVICE_IDS:
        seq = load_series(did)
        print(f"设备 {did}: 有效样本点 {len(seq)}")
        if len(seq) < T + 50:
            print("  数据不足，跳过")
            continue
        X, y = make_samples(seq)
        all_x.append(X)
        all_y.append(y)

    if not all_x:
        print("错误：无足够训练数据，请先运行系统让模拟器产生数据")
        sys.exit(1)

    X = np.concatenate(all_x)
    y = np.concatenate(all_y)
    print(f"训练样本数: {X.shape[0]}")

    # 归一化
    scaler = StandardScaler()
    X_flat = X.reshape(-1, X.shape[-1])
    scaler.fit(X_flat)
    X_norm = scaler.transform(X_flat).reshape(X.shape)
    y_load = y[:, 0].reshape(-1, 1)
    load_scaler = StandardScaler()
    load_scaler.fit(y_load)
    y_load_norm = load_scaler.transform(y_load).flatten()
    y_risk = y[:, 1]

    with open(os.path.join(HERE, "scaler.pkl"), "wb") as f:
        pickle.dump({"feature_scaler": scaler, "load_scaler": load_scaler}, f)

    # 训练/验证划分
    n = X.shape[0]
    idx = np.random.permutation(n)
    split = int(n * 0.85)
    train_idx, val_idx = idx[:split], idx[split:]

    model = CranePredictNet()
    opt = torch.optim.Adam(model.parameters(), lr=LR)
    load_loss_fn = nn.MSELoss()
    risk_loss_fn = nn.BCEWithLogitsLoss()

    X_t = torch.tensor(X_norm[train_idx])
    yl_t = torch.tensor(y_load_norm[train_idx])
    yr_t = torch.tensor(y_risk[train_idx])
    X_v = torch.tensor(X_norm[val_idx])
    yl_v = torch.tensor(y_load_norm[val_idx])
    yr_v = torch.tensor(y_risk[val_idx])

    for epoch in range(1, EPOCHS + 1):
        model.train()
        perm = torch.randperm(X_t.size(0))
        total = 0.0
        for i in range(0, X_t.size(0), BATCH_SIZE):
            b = perm[i:i + BATCH_SIZE]
            pred = model(X_t[b])
            loss = load_loss_fn(pred[:, 0], yl_t[b]) + 0.5 * risk_loss_fn(pred[:, 1], yr_t[b])
            opt.zero_grad()
            loss.backward()
            opt.step()
            total += loss.item() * b.size(0)
        if epoch % 10 == 0 or epoch == 1:
            model.eval()
            with torch.no_grad():
                vpred = model(X_v)
                vloss = load_loss_fn(vpred[:, 0], yl_v).item() + 0.5 * risk_loss_fn(vpred[:, 1], yr_v).item()
                acc = ((vpred[:, 1] > 0).float() == yr_v).float().mean().item()
            print(f"Epoch {epoch:3d} | train_loss {total / X_t.size(0):.4f} | val_loss {vloss:.4f} | risk_acc {acc:.3f}")

    # 保存模型
    torch.save({"state_dict": model.state_dict(),
                "config": {"in_dim": 5, "hidden": 64, "num_layers": 2, "out_dim": 2},
                "t": T, "features": FEATURES,
                "load_warn": LOAD_WARN, "wind_warn": WIND_WARN},
               os.path.join(HERE, "crane_model.pt"))
    print("模型已保存: predict/crane_model.pt")


if __name__ == "__main__":
    main()
