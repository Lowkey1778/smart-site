import request from './request'

// ============ Coze 智能体（T-31 / RQ-38） ============
// 注意：Coze 智能体真实响应需 10~30s（后端轮询上限 30s），全局 axios 超时仅 10s，
// 必须为聊天请求单独放宽超时（曾因超时导致前端报"智能体服务暂时不可用"）
export const cozeChat = data => request.post('/coze/chat', data, { timeout: 60000 })
export const getQuickQuestions = () => request.get('/coze/quick-questions')
export const getCozeConfig = () => request.get('/coze/config')
export const saveCozeConfig = data => request.put('/coze/config', data)

// ============ 设备通信模拟平台（T-32 / 接口4.2） ============
export const getIotOverview = () => request.get('/iot/overview')
export const getIotConnections = () => request.get('/iot/connections')
export const getIotRecords = params => request.get('/iot/records', { params })

// ============ 手动推送（演示人调节推送数据） ============
export const iotSimPush = data => request.post('/iot/sim/push', data)          // 单次推送
export const iotSimStart = data => request.post('/iot/sim/start', data)        // 周期推送
export const iotSimStop = data => request.post('/iot/sim/stop', data)          // 停止推送
export const getIotSimStatus = () => request.get('/iot/sim/status')
