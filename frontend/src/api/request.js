import axios from 'axios'
import { ElMessage } from 'element-plus'

// axios 实例：基础路径 /api（开发环境由 Vite 代理到后端 8080）
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动携带 Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// HTTP 状态码 -> 友好提示文案（中英双语，不暴露后端原始 error）
const STATUS_MESSAGES = {
  400: '请求参数有误，请检查输入后重试 / Invalid request parameters',
  401: '登录已过期，请重新登录 / Session expired, please sign in again',
  403: '没有权限执行该操作 / Permission denied',
  404: '请求的资源不存在或已被删除 / Resource not found',
  405: '请求方式不被支持 / Method not allowed',
  409: '数据冲突，请刷新后重试 / Data conflict, please retry',
  422: '数据校验未通过，请检查输入 / Validation failed',
  500: '服务器繁忙，请稍后重试 / Server busy, please try again later',
  502: '网关响应异常，请稍后重试 / Bad gateway',
  503: '服务暂时不可用，请稍后重试 / Service temporarily unavailable',
  504: '服务响应超时，请稍后重试 / Gateway timeout'
}

/** 清洗后端返回的错误消息：过滤原始异常/堆栈/英文 error，保证弹窗内容友好 */
const cleanMessage = msg => {
  if (!msg) return ''
  const s = String(msg)
  // 后端泄露的原始异常信息（堆栈、java 异常、axios 英文报错）一律替换为通用文案
  if (/Exception|Caused by|^\s*at |Error:|error|failed|undefined|NaN/i.test(s)) {
    return ''
  }
  return s.trim()
}

// 响应拦截器：统一处理 code / 401
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 0) {
      let msg = cleanMessage(res.message)
      if (!msg) msg = res.code === 500 ? STATUS_MESSAGES[500] : '操作失败，请稍后重试 / Operation failed'
      ElMessage.error(msg)
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  error => {
    // 请求超时
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络后重试 / Request timeout')
      return Promise.reject(error)
    }
    const status = error.response?.status
    // 401 统一跳转登录
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      ElMessage.warning('登录已过期，请重新登录')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    if (status) {
      // 优先取后端返回的业务消息（已过滤异常痕迹），否则用状态码文案
      let msg = cleanMessage(error.response?.data?.message)
      if (!msg) msg = STATUS_MESSAGES[status] || '操作失败，请稍后重试 / Operation failed'
      ElMessage.error(msg)
    } else {
      ElMessage.error('无法连接服务器，请检查网络 / Network error')
    }
    return Promise.reject(error)
  }
)

export default request
