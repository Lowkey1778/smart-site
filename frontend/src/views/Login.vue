<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-title">
        <el-icon :size="36" color="#409EFF"><Monitor /></el-icon>
        <h2>建筑安全智能监控平台</h2>
        <p>Building Safety Intelligent Monitoring Platform</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">演示账号：admin / 123456（系统管理员）、leader / 123456、safety / 123456</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { getMyMenus, getMyPerms } from '../api/sys'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

// 登录表单初始为空，不自动填充任何账号密码（用户手动输入）
const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate(async valid => {
    if (!valid) return
    loading.value = true
    try {
      const data = await request.post('/auth/login', form)
      localStorage.setItem('token', data.token)
      localStorage.setItem('userInfo', JSON.stringify(data))
      // 拉取当前角色可访问菜单与按钮权限（用于动态菜单 + 路由守卫 + 按钮控制）
      try {
        const menus = await getMyMenus()
        localStorage.setItem('menus', JSON.stringify(menus))
        const perms = await getMyPerms()
        localStorage.setItem('perms', JSON.stringify(perms))
      } catch (e) {
        localStorage.removeItem('menus')
        localStorage.removeItem('perms')
      }
      ElMessage.success('登录成功')
      router.push('/home')
    } catch (e) {
      // 错误信息已由拦截器提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%);
}
.login-box {
  width: 400px;
  padding: 40px 36px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3);
}
.login-title {
  text-align: center;
  margin-bottom: 28px;
}
.login-title h2 {
  margin: 10px 0 4px;
  color: #303133;
}
.login-title p {
  font-size: 12px;
  color: #909399;
}
.login-btn {
  width: 100%;
}
.login-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
