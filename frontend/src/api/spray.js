import request from './request'

// ============ 喷淋降尘（T-24~T-27） ============
// 操作记录
export const getSprayRecords = params => request.get('/spray/record', { params })
// 定时任务
export const getSprayTasks = () => request.get('/spray/task/list')
export const addSprayTask = data => request.post('/spray/task', data)
export const updateSprayTask = (id, data) => request.put(`/spray/task/${id}`, data)
export const deleteSprayTask = id => request.delete(`/spray/task/${id}`)
export const changeSprayTaskStatus = (id, status) => request.put(`/spray/task/${id}/status`, { status })
// 手动控制
export const sprayManual = data => request.post('/spray/manual', data)
// 位置
export const getSprayLocations = () => request.get('/spray/locations')
// 喷淋设备状态总览（卡片式主界面：设备状态 + 周边湿度）
export const getSprayStatus = () => request.get('/spray/status')