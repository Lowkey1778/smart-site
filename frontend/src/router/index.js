import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyMenus } from '../api/sys'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/login',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('../views/Home.vue'),
        meta: { title: '首页/工作台' }
      },
      {
        path: 'device',
        name: 'DeviceList',
        component: () => import('../views/DeviceList.vue'),
        meta: { title: '设备资产管理' }
      },
      {
        path: 'device/:id',
        name: 'DeviceDetail',
        component: () => import('../views/DeviceDetail.vue'),
        meta: { title: '设备详情' }
      },
      {
        path: 'crane',
        name: 'CraneMonitor',
        component: () => import('../views/CraneMonitor.vue'),
        meta: { title: '塔吊监控' }
      },
      {
        path: 'lift',
        name: 'LiftMonitor',
        component: () => import('../views/LiftMonitor.vue'),
        meta: { title: '升降机监控' }
      },
      {
        path: 'env',
        name: 'EnvMonitor',
        component: () => import('../views/EnvMonitor.vue'),
        meta: { title: '环境监测' }
      },
      {
        path: 'video',
        name: 'VideoMonitor',
        component: () => import('../views/VideoMonitor.vue'),
        meta: { title: '视频监控' }
      },
      {
        path: 'ai',
        name: 'AiAlarm',
        component: () => import('../views/AiAlarm.vue'),
        meta: { title: 'AI智能识别' }
      },
      {
        path: 'alarm',
        name: 'AlarmManage',
        component: () => import('../views/AlarmManage.vue'),
        meta: { title: '告警管理' }
      },
      {
        path: 'alarm/stats',
        name: 'AlarmStats',
        component: () => import('../views/AlarmStats.vue'),
        meta: { title: '告警统计分析' }
      },
      {
        path: 'spray',
        name: 'SprayMonitor',
        component: () => import('../views/SprayMonitor.vue'),
        meta: { title: '喷淋降尘' }
      },
      {
        path: 'iot',
        name: 'IotMonitor',
        component: () => import('../views/IotMonitor.vue'),
        meta: { title: '设备通信模拟平台' }
      },
      {
        path: 'coze',
        name: 'CozeChat',
        component: () => import('../views/CozeChat.vue'),
        meta: { title: 'Coze智能体' }
      },      {
        path: 'scene',
        name: 'Scene3D',
        component: () => import('../views/Scene3D.vue'),
        meta: { title: '3D可视化' }
      },
      {
        path: 'screen',
        name: 'DataScreen',
        component: () => import('../views/DataScreen.vue'),
        meta: { title: '数据大屏' }
      },
      {
        path: 'system/user',
        name: 'UserManage',
        component: () => import('../views/UserManage.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'system/role',
        name: 'RoleManage',
        component: () => import('../views/RoleManage.vue'),
        meta: { title: '角色管理' }
      },
      {
        path: 'system/log',
        name: 'OperationLog',
        component: () => import('../views/OperationLog.vue'),
        meta: { title: '操作日志' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' },
  // 兼容旧菜单路径：3D 菜单 path 为 /three，路由为 /scene
  { path: '/three', redirect: '/scene' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 收集用户可访问的菜单路径集合（来自登录后缓存的 menus）
const collectAllowedPaths = () => {
  const paths = new Set(['/home'])
  try {
    const menus = JSON.parse(localStorage.getItem('menus') || '[]')
    const walk = nodes => {
      nodes.forEach(n => {
        if (n.path) paths.add(n.path)
        if (n.children && n.children.length) walk(n.children)
      })
    }
    walk(menus)
  } catch (e) {
    // 缓存异常时按无限制处理
  }
  return paths
}

// 路由守卫：未登录跳登录页；登录页始终可访问（即使已有会话，打开链接即要求登录）
router.beforeEach(async (to, from, next) => {
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + '建筑安全智能监控平台'
  const token = localStorage.getItem('token')
  // 登录页始终放行：每次打开/访问链接都先进入登录界面
  if (to.path === '/login') {
    next()
    return
  }
  if (!token) {
    next('/login')
    return
  }
  // 已登录但 menus 缓存缺失/被清：主动拉取当前账号菜单，拉不到则要求重新登录
  let menus = localStorage.getItem('menus')
  if (token && !menus) {
    try {
      const res = await getMyMenus()
      localStorage.setItem('menus', JSON.stringify(res))
      menus = JSON.stringify(res)
    } catch (e) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      next('/login')
      return
    }
  }
  // 菜单权限校验：精确匹配，或命中带子路径的动态路由（如 /device/1 属于 /device）
  if (token && menus && to.path !== '/home') {
    const allowed = collectAllowedPaths()
    const isAllowed = allowed.has(to.path)
      || [...allowed].some(p => p !== '/home' && to.path.startsWith(p + '/'))
    if (!isAllowed) {
      ElMessage.warning('当前账号无权限访问该页面')
      next('/home')
      return
    }
  }
  next()
})

export default router
