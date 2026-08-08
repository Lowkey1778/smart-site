import request from './request'

// ============ 系统管理：用户管理 ============
export const getUserPage = params => request.get('/sys/user/page', { params })
export const getUserDetail = id => request.get(`/sys/user/${id}`)
export const addUser = data => request.post('/sys/user', data)
export const updateUser = (id, data) => request.put(`/sys/user/${id}`, data)
export const deleteUser = id => request.delete(`/sys/user/${id}`)
export const resetUserPassword = (id, password) => request.put(`/sys/user/${id}/password`, { password })
export const changeUserStatus = (id, status) => request.put(`/sys/user/${id}/status`, { status })

// ============ 系统管理：角色管理 ============
export const getRoleList = () => request.get('/sys/role/list')
export const getMenuTree = () => request.get('/sys/role/menu-tree')
export const addRole = data => request.post('/sys/role', data)
export const updateRole = (id, data) => request.put(`/sys/role/${id}`, data)
export const deleteRole = id => request.delete(`/sys/role/${id}`)

// ============ 认证：当前用户菜单 / 按钮权限 ============
export const getMyMenus = () => request.get('/auth/menus')
export const getMyPerms = () => request.get('/auth/perms')
export const changeMyPassword = data => request.post('/auth/change-password', data)
export const logout = () => request.post('/auth/logout')
