/**
 * 设备通信模拟平台（T-32 / 接口章节 4.2）
 * -------------------------------------------------
 * 1. Express 控制台 :8082 —— 展示设备连接状态与上报记录
 * 2. TCP 客户端 —— 模拟多台设备通过 TCP 长连接向后端(9001)上报数据
 *
 * 启动：node app.js   （先启动后端，后端 9001 端口监听 TCP）
 * 控制台：http://localhost:8082
 */
'use strict';

const net = require('net');
const express = require('express');
const path = require('path');

const TCP_HOST = '127.0.0.1';
const TCP_PORT = 9001;      // 后端 IotTcpServer 监听端口
const WEB_PORT = 8082;      // 本平台控制台端口
const REPORT_INTERVAL = 5000; // 上报周期(ms)

/* ---------------- 环境数据合理性：随机游走 + 温湿度关联 + 故障注入 ----------------
 * 温度/湿度/PM2.5/噪声/风速 均在上一值基础上小步长渐变（偏线性、不跳变）；
 * 湿度与温度近似线性负相关：湿度 = 88 - (温度-20)×3.2 + 噪声；
 * PM10 与 PM2.5 关联：PM10 ≈ PM2.5×1.8；
 * 小概率触发 PM2.5/噪声/风速异常事件（持续数十个周期），用于演示告警检测。
 */
function createEnvState() {
  return {
    'PM2.5': 45 + Math.random() * 10,
    'PM10': 90 + Math.random() * 15,
    '噪声': 60 + Math.random() * 6,
    '温度': 26 + Math.random() * 4,
    '湿度': 62 + Math.random() * 8,
    '风速': 4 + Math.random() * 3,
    fault: { key: null, remain: 0 },
  };
}

/** 单步随机游走：cur ± step（中心附近），夹在 [min,max]；faultTarget 非空时向故障值抬升 */
function walk(cur, step, min, max, faultTarget) {
  let v;
  if (faultTarget != null) {
    v = cur + (faultTarget - cur) * 0.35 + (Math.random() - 0.5) * step * 2; // 异常期快速渐变抬升
  } else {
    v = cur + (Math.random() - 0.5) * step * 2;                              // 正常期平滑游走
  }
  return Math.max(min, Math.min(max, v));
}

/** 生成一帧环境数据（温湿度关联、PM10 关联、故障注入） */
function genEnv(state) {
  const s = state || createEnvState();
  const rnd = Math.random();
  // 故障注入：1.2%/周期 触发 PM2.5/噪声/风速 异常（持续 24~60 周期）
  if (!s.fault.key && rnd < 0.012) {
    const keys = ['PM2.5', '噪声', '风速'];
    s.fault.key = keys[Math.floor(Math.random() * keys.length)];
    s.fault.remain = 24 + Math.floor(Math.random() * 37);
    console.log(`[ENV-FAULT] ${s.fault.key} 异常事件触发（演示告警检测）`);
  }
  const faultTargets = { 'PM2.5': 160, '噪声': 92, '风速': 19 };

  // 基础量随机游走（故障期向目标抬升）
  s['PM2.5'] = walk(s['PM2.5'], 2.5, 25, 165, s.fault.key === 'PM2.5' ? faultTargets['PM2.5'] : null);
  s['噪声'] = walk(s['噪声'], 1.5, 52, 95, s.fault.key === '噪声' ? faultTargets['噪声'] : null);
  s['风速'] = walk(s['风速'], 0.7, 2, 20, s.fault.key === '风速' ? faultTargets['风速'] : null);
  // 温度小步长游走（不注入故障，保持平稳）
  s['温度'] = walk(s['温度'], 0.35, 20, 36, null);
  // 湿度：由温度线性推导（温度↑湿度↓），再叠加小噪声
  s['湿度'] = Math.max(20, Math.min(98, 88 - (s['温度'] - 20) * 3.2 + (Math.random() - 0.5) * 3));
  // PM10：与 PM2.5 关联
  s['PM10'] = Math.max(30, s['PM2.5'] * 1.8 + (Math.random() - 0.5) * 4);

  if (s.fault.key) {
    s.fault.remain -= 1;
    if (s.fault.remain <= 0) s.fault.key = null;
  }

  return {
    'PM2.5': +s['PM2.5'].toFixed(1),
    'PM10': +s['PM10'].toFixed(1),
    '噪声': +s['噪声'].toFixed(1),
    '温度': +s['温度'].toFixed(1),
    '湿度': +s['湿度'].toFixed(1),
    '风速': +s['风速'].toFixed(1),
  };
}

/* ---------------- 虚拟设备定义（与 t_device 表一致） ---------------- */
const DEVICES = [
  {
    deviceCode: 'TC-001',
    name: '1#塔吊',
    type: 'crane',
    gen: () => ({
      load: +(0.8 + Math.random() * 6.5).toFixed(2),
      radius: +(12 + Math.random() * 40).toFixed(1),
      wind_speed: +(2 + Math.random() * 14).toFixed(1),
      height: +(8 + Math.random() * 95).toFixed(1),
      angle: +(Math.random() * 360).toFixed(1),
    }),
  },
  {
    deviceCode: 'TC-002',
    name: '2#塔吊',
    type: 'crane',
    gen: () => ({
      load: +(0.8 + Math.random() * 6.5).toFixed(2),
      radius: +(12 + Math.random() * 40).toFixed(1),
      wind_speed: +(2 + Math.random() * 14).toFixed(1),
      height: +(8 + Math.random() * 95).toFixed(1),
      angle: +(Math.random() * 360).toFixed(1),
    }),
  },
  {
    deviceCode: 'LFT-001',
    name: '1#施工升降机',
    type: 'lift',
    gen: () => {
      const bothOpen = Math.random() < 0.05; // 5% 概率双门同开（安全隐患）
      return {
        load_weight: Math.round(300 + Math.random() * 1650),
        person_count: 1 + Math.floor(Math.random() * 8),
        height: +(5 + Math.random() * 115).toFixed(1),
        wind_speed: +(2 + Math.random() * 12).toFixed(1),
        direction: Math.random() < 0.5 ? 1 : 2,
        door_front: bothOpen || Math.random() < 0.5 ? 1 : 0,
        door_back: bothOpen || Math.random() < 0.5 ? 1 : 0,
      };
    },
  },
  {
    deviceCode: 'ENV-001',
    name: '环境监测站1',
    type: 'env',
    state: createEnvState(),
    gen: function () { return genEnv(this.state); },
  },
  {
    deviceCode: 'ENV-002',
    name: '环境监测站2',
    type: 'env',
    state: createEnvState(),
    gen: function () { return genEnv(this.state); },
  },
];

/* ---------------- 运行状态（供控制台查询） ---------------- */
const stats = {
  startedAt: new Date().toISOString(),
  totalReports: 0,
  devices: DEVICES.map(d => ({
    deviceCode: d.deviceCode,
    name: d.name,
    type: d.type,
    connected: false,
    connectTime: null,
    lastReportTime: null,
    lastPayload: null,
    reportCount: 0,
  })),
};

function deviceStat(code) {
  return stats.devices.find(d => d.deviceCode === code);
}

/* ---------------- TCP 客户端：每台设备一条长连接 ---------------- */
function startDevice(d) {
  const stat = deviceStat(d.deviceCode);
  let socket = null;
  let timer = null;

  const connect = () => {
    socket = new net.Socket();
    socket.setKeepAlive(true, 3000);

    socket.connect(TCP_PORT, TCP_HOST, () => {
      stat.connected = true;
      stat.connectTime = new Date().toISOString();
      console.log(`[${d.deviceCode}] ${d.name} TCP 连接成功 ${TCP_HOST}:${TCP_PORT}`);
      if (!timer) {
        timer = setInterval(report, REPORT_INTERVAL);
        report(); // 连接后立即上报一次
      }
    });

    socket.on('error', err => {
      console.log(`[${d.deviceCode}] 连接错误: ${err.code}`);
      socket.destroy();
    });

    socket.on('close', () => {
      stat.connected = false;
      stat.connectTime = null;
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
      console.log(`[${d.deviceCode}] 连接断开，3 秒后重连`);
      setTimeout(connect, 3000);
    });
  };

  const report = () => {
    const payload = JSON.stringify({
      deviceCode: d.deviceCode,
      type: d.type,
      data: d.gen(),
      ts: Date.now(),
    });
    if (socket && !socket.destroyed) {
      socket.write(payload + '\n');
      stat.lastReportTime = new Date().toISOString();
      stat.lastPayload = payload;
      stat.reportCount++;
      stats.totalReports++;
    }
  };

  connect();
}

/* ---------------- Express 控制台 ---------------- */
const app = express();
app.use(express.static(path.join(__dirname, 'public')));

app.get('/api/status', (req, res) => {
  res.json(stats);
});

app.listen(WEB_PORT, () => {
  console.log('========================================');
  console.log('设备通信模拟平台（Express + TCP）');
  console.log(`控制台: http://localhost:${WEB_PORT}`);
  console.log(`TCP 上报目标: ${TCP_HOST}:${TCP_PORT}，周期 ${REPORT_INTERVAL / 1000}s`);
  console.log(`模拟设备数: ${DEVICES.length}`);
  console.log('========================================');
});

DEVICES.forEach(startDevice);
