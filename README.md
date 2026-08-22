# 建筑安全智能监控平台 — 最小可运行骨架

> 技术栈：SpringBoot 3.3.5 + MyBatis-Plus + MySQL 8 + JWT（后端）
> Vue 3 + Vite + Element Plus（前端）
> 对应文档：《软件需求规格说明书》《数据库设计报告》《概要设计说明书（接口设计）》

## 一、环境要求（本机已配置）

| 组件 | 版本 | 位置/说明 |
|---|---|---|
| JDK | 21 LTS | 已装（SpringBoot 3.3 要求 17+） |
| Maven | 3.9.9 | `D:\Java\maven\apache-maven-3.9.9-bin\apache-maven-3.9.9`（已配置阿里云镜像） |
| Node.js | 23.x + npm 10.x | 已装 |
| MySQL | 8.4.4 | `D:\software\MySQL`，服务已启动 |
| Redis | 未装 | 最小闭环暂不需要（JWT 无状态）；完整版按计划书接入 |

## 二、首次初始化（只做一次）

### 1. 建库 + 建表 + 初始数据

```bash
# 在 MySQL 命令行执行（密码换成你自己的 root 密码）
mysql -uroot -p
source D:/NPU/IWANTGOOHOME/smart-site/sql/smart_site.sql     # 建库建表（20 张表）
source D:/NPU/IWANTGOOHOME/smart-site/sql/init_data.sql   # 初始数据（3 个账号、菜单、设备）
```

初始账号（密码均为 `123456`，BCrypt 加密存储）：

| 账号 | 角色 | 说明 |
|---|---|---|
| admin | 系统管理员 | 全部功能 |
| leader | 项目经理/领导 | 只读为主 |
| safety | 安全管理员 | 日常操作 |

### 2. 配置数据库密码

编辑 `backend/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    password: 你的MySQL密码   # 或设置环境变量 DB_PASSWORD 覆盖
```

## 三、启动（IntelliJ IDEA 方式）

> 前端页面已打包进后端（`backend/src/main/resources/static`），**只用 IDEA 跑后端即可看到完整系统**，无需单独启动前端。

### 后端（核心，演示必做）

1. 打开 **IntelliJ IDEA**（`D:\IT_SoftWare\IntelliJ IDEA 2024.3`）
2. **File → Open** → 选择 `D:\work\smart-site\backend` 文件夹（含 pom.xml）
3. 首次打开等右下角 Maven 依赖下载完成（已配阿里云镜像）
4. **File → Project Structure → Project** → 确认 SDK 为 21
5. 左侧展开 `src/main/java/com/qst/smartsite` → 右键 **SmartSiteApplication** → **Run 'SmartSiteApplication'**
6. 控制台出现 `Started SmartSiteApplication` 后，浏览器打开 **http://localhost:8080**，用 `admin / 123456` 登录

### 前端（只有改前端代码时才需要）

1. IDEA 里 **File → Open** → 选择 `D:\work\smart-site\frontend`（新窗口）
2. 打开 `package.json` → 点 `dev` 脚本左侧的绿色 ▶（IDEA 会自动识别 npm 脚本）
3. 浏览器打开 **http://localhost:5173**（改代码即时热更新）
4. 前端改完要重新构建进发布版：`frontend` 目录终端执行 `npm run build`，然后把 `dist` 内容复制到 `backend/src/main/resources/static`（由组长/负责人统一操作）


## 四、扩展功能服务（T-18/T-31/T-32/T-33/T-34）

> 基础演示（IDEA 跑后端）不依赖以下服务；以下功能需要额外服务：

| 功能 | 服务 | 启动命令 |
|---|---|---|
| T-18 真实视觉推理 | AI 推理服务 :5000 | `ai-server` 下执行 `.venv\Scripts\python.exe app.py`（YOLOv8n，模型在 ai-server/yolov8n.pt） |
| T-33 塔吊状态预测 | 预测服务 :5001 | `ai-server` 下执行 `.venv\Scripts\python.exe predict\predict_server.py`（模型已训练：predict/crane_model.pt） |
| T-32 设备通信模拟平台 | 模拟器控制台 :8082 + TCP:9001 | `simulator` 下执行 `npm install && node app.js` |
| T-34 Redis 缓存与会话 | Redis :6379 | `D:\IT_SoftWare\redis\redis-server.exe`（可注册 Windows 服务） |
| T-31 Coze 智能体 | 无独立服务 | 默认本地知识引擎；配置 `application.yml` 的 `coze.api-token/bot-id` 后接入真实 Coze |

> 注：T-33 模型训练脚本 `ai-server/predict/train_crane_model.py`（从 t_realtime_data 读取历史数据训练 CNN+LSTM+Attention）。

## 五、已验证的接口（Postman/Apifox 可导入）

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/auth/login` | POST | 登录，返回 JWT token |
| `/api/auth/info` | GET | 当前用户信息（需 Bearer token） |
| `/api/device/list` | GET | 设备台账列表（需 Bearer token） |
| `/api/device/{id}` | GET | 设备详情 |
| `/api/crane/list` | GET | 塔吊实时状态（力矩=吊重×幅度 实时计算） |
| `/api/crane/{id}` | GET | 塔吊监控详情 |
| `/api/lift/list` | GET | 升降机实时状态（载重/门锁/超员） |
| `/api/lift/{id}` | GET | 升降机监控详情 |
| `/api/env/points` | GET | 环境监测点实时数据 |
| `/api/env/history?pointId=&hours=` | GET | 环境历史趋势（默认24小时） |
| `/api/alarm/list?pageNum=&pageSize=` | GET | 告警分页（支持级别/来源/状态筛选） |
| `/api/alarm/{id}/handle` | PUT | 告警处置（填处置人/措施/结论） |
| `/api/alarm/stats` | GET | 告警统计（级别/状态/来源/7天趋势） |
| `/api/dashboard/stats` | GET | 首页统计（在线/离线/今日告警/未处理） |
| `/api/dashboard/overview` | GET | 数据大屏聚合数据 |
| WebSocket `/ws` | WS | 实时推送塔吊/升降机/环境最新状态（5秒/次） |

统一返回结构：`{"code":0,"message":"success","data":{...}}`，与《接口设计》通用约定一致。

## 五、数据模拟器说明

- `MockDataScheduler` 每 5 秒生成塔吊（吊重/幅度/风速/高度/角度）、升降机（载重/人数/门锁/方向）、环境（PM2.5/PM10/噪声/温度/湿度/风速）数据
- 超阈值自动生成三级告警（1-预警 / 2-警报 / 3-控制），10 分钟内同事件不重复告警
- 数据通过 WebSocket 实时推送到前端页面，无需手动刷新

## 六、项目结构

```
smart-site/
├── backend/                 # SpringBoot 后端
│   └── src/main/java/com/qst/smartsite/
│       ├── config/          # JWT 工具、拦截器、WebMvc 配置
│       ├── controller/      # AuthController / DeviceController
│       ├── dto/             # 请求/响应对象
│       ├── entity/          # 实体（对应数据表）
│       ├── mapper/          # MyBatis-Plus Mapper
│       └── common/          # 统一返回、异常处理
├── frontend/                # Vue3 前端
│   └── src/
│       ├── api/             # axios 封装（自动带 token、401 跳登录）
│       ├── router/          # 路由 + 登录守卫
│       ├── layouts/         # 主布局（侧边菜单/顶栏）
│       └── views/           # 登录页 / 首页 / 设备列表
├── sql/init_data.sql        # 初始数据


## 七、下一步开发计划（按优先级）

1. **Express 设备模拟器**（TCP 上报塔吊/环境数据）→ 数据动起来
2. **告警模块**：阈值判断 → 生成告警 → WebSocket 推送
3. **塔吊/升降机监控页**（实时数据 + 力矩计算）
4. 环境监测、喷淋联动、AI 识别、视频、大屏、3D、Coze（见项目计划书 WBS）
