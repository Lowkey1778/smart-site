import request from './request'

// ============ 塔吊监控 ============
export const getCraneList = () => request.get('/crane/list')
export const getCraneDetail = id => request.get(`/crane/${id}`)
// 塔吊安全状态预测（T-33 / RQ-17）
export const getCranePredict = id => request.get(`/crane/predict/${id}`)
export const getCranePredictHealth = () => request.get('/crane/predict/health')

// ============ 升降机监控 ============
export const getLiftList = () => request.get('/lift/list')
export const getLiftDetail = id => request.get(`/lift/${id}`)
export const getCraneRecords = params => request.get('/crane/records', { params })
export const getLiftRecords = params => request.get('/lift/records', { params })

// ============ 环境监测 ============
export const getEnvPoints = () => request.get('/env/points')
export const getEnvHistory = (pointId, hours = 24) => request.get('/env/history', { params: { pointId, hours } })

// ============ 告警管理 ============
export const getAlarmList = params => request.get('/alarm/list', { params })
export const handleAlarm = (id, data) => request.put(`/alarm/${id}/handle`, data)
export const startHandleAlarm = (id, data) => request.put(`/alarm/${id}/start-handle`, data)
export const getAlarmStats = params => request.get('/alarm/stats', { params })
export const getRelatedAlarms = id => request.get(`/alarm/${id}/related`)

// ============ 数据大屏 / 首页 ============
export const getDashboardStats = () => request.get('/dashboard/stats')
export const getDashboardOverview = () => request.get('/dashboard/overview')

// ============ 环境监测点管理（T-22）/ 日统计（T-23） ============
export const addEnvPoint = data => request.post('/env/point', data)
export const updateEnvPoint = (id, data) => request.put(`/env/point/${id}`, data)
export const deleteEnvPoint = id => request.delete(`/env/point/${id}`)
export const getEnvDailyStats = (pointId, days = 1) => request.get('/env/daily-stats', { params: { pointId, days } })