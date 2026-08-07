import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 全局注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus, { locale: zhCn })
app.use(router)

// 按钮级权限指令：无权限时移除元素，如 v-permission="'sys:user:add'"
app.directive('permission', {
  mounted(el, binding) {
    let perms = []
    try {
      perms = JSON.parse(localStorage.getItem('perms') || '[]')
    } catch (e) {
      perms = []
    }
    const need = binding.value
    if (need && !perms.includes(need)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
})

app.mount('#app')
