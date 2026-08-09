import request from './request'

// ============ 设备台账 ============
export const getDevicePage = params => request.get('/device/page', { params })
export const getDeviceDetail = id => request.get(`/device/${id}`)
export const getDeviceDetailAgg = id => request.get(`/device/${id}/detail`)
export const addDevice = data => request.post('/device', data)
export const updateDevice = (id, data) => request.put(`/device/${id}`, data)
export const deleteDevice = id => request.delete(`/device/${id}`)

// ============ 设备类型管理 ============
export const getDeviceTypeTree = () => request.get('/device-type/tree')
export const addDeviceType = data => request.post('/device-type', data)
export const updateDeviceType = (id, data) => request.put(`/device-type/${id}`, data)
export const deleteDeviceType = id => request.delete(`/device-type/${id}`)

// ============ 设备位置管理 ============
export const getDeviceLocationTree = () => request.get('/device-location/tree')
export const addDeviceLocation = data => request.post('/device-location', data)
export const updateDeviceLocation = (id, data) => request.put(`/device-location/${id}`, data)
export const deleteDeviceLocation = id => request.delete(`/device-location/${id}`)

// ============ 设备监测点 ============
export const getDevicePoints = id => request.get(`/device/${id}/points`)
export const addDevicePoint = data => request.post('/device/point', data)
export const updateDevicePoint = (pointId, data) => request.put(`/device/point/${pointId}`, data)
export const deleteDevicePoint = pointId => request.delete(`/device/point/${pointId}`)

// ============ 实时/历史数据 / 离线记录 ============
export const getDeviceRealtime = id => request.get(`/device/${id}/realtime`)
export const getDeviceHistory = (id, params) => request.get(`/device/${id}/history`, { params })
export const getOfflineRecords = (id, params) => request.get(`/device/${id}/offline-records`, { params })
export const getOnlineRate = (id, params) => request.get(`/device/${id}/online-rate`, { params })
