<template>
  <el-container class="layout">
    <!-- 左侧菜单（按角色权限动态渲染） -->
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="26" color="#409EFF"><Monitor /></el-icon>
        <span>智慧工地监控平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#c0c4cc"
        active-text-color="#409EFF"
      >
        <template v-for="m in visibleMenus" :key="m.id">
          <el-sub-menu v-if="m.children && m.children.length" :index="'menu-' + m.id">
            <template #title>
              <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
              <span>{{ m.menuName }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.id" :index="c.path || 'menu-' + c.id">
              <el-icon v-if="c.icon"><component :is="c.icon" /></el-icon>
              <span>{{ c.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.path || 'menu-' + m.id">
            <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
            <span>{{ m.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <el-badge :value="alarmCount" :hidden="alarmCount === 0" :max="99" class="alarm-badge">
            <el-icon :size="20" class="alarm-icon" @click="goAlarm"><Bell /></el-icon>
          </el-badge>
          <el-tag v-if="roles.length" size="small" type="success" class="role-tag">
            {{ roleName }}
          </el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-name">
              <el-icon><User /></el-icon>
              {{ userInfo.realName || userInfo.username }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="changePwd">
                  <el-icon><Lock /></el-icon>重置密码
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 重置密码弹窗（右上角用户区进入） -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="440px" destroy-on-close>
      <el-form :model="pwdForm" label-width="90px">
        <el-form-item label="原密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前登录密码" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="不少于 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-alert type="info" :closable="false"
          title="重置成功后需使用新密码重新登录，密码将实时保存至系统。" />
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitChangePwd">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 右下角小机器人悬浮球：点击直达 Coze 智能体（替代左侧菜单入口） -->
    <div class="robot-ball" @click="robotVisible = true" title="智慧工地安全助手">
      <span class="robot-face">🤖</span>
      <span class="robot-badge"></span>
    </div>

    <!-- Coze 智能体抽屉 -->
    <el-drawer v-model="robotVisible" title="Coze 智能体 · 智慧工地安全助手" size="480px" append-to-body
      :destroy-on-close="false" class="robot-drawer">
      <CozeChat embedded />
    </el-drawer>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage, ElNotification } from 'element-plus'
import wsClient from '../api/ws'
import { getMyMenus, getMyPerms, changeMyPassword, logout } from '../api/sys'
import CozeChat from '../views/CozeChat.vue'

const route = useRoute()
const router = useRouter()

// 悬浮球智能体抽屉
const robotVisible = ref(false)

// 左侧菜单过滤：Coze 智能体改为右下角悬浮球入口，不再占用侧边栏
const visibleMenus = computed(() => {
  const hidePath = p => p && (p === '/coze' || p.startsWith('/coze'))
  return menus.value
    .filter(m => !hidePath(m.path) && !(m.children || []).some(c => hidePath(c.path)))
    .map(m => ({
      ...m,
      children: (m.children || []).filter(c => !hidePath(c.path))
    }))
})

const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
const roles = userInfo.roles || []

const roleName = computed(() => {
  const map = { ADMIN: '系统管理员', LEADER: '项目经理', SAFETY: '安全管理员' }
  return roles.map(r => map[r] || r).join(' / ')
})

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')

// 当前用户可访问菜单（登录后拉取并存 localStorage，供路由守卫与刷新恢复）
const menus = ref(JSON.parse(localStorage.getItem('menus') || '[]'))

const loadMenus = async () => {
  try {
    menus.value = await getMyMenus()
    localStorage.setItem('menus', JSON.stringify(menus.value))
    // 按钮权限随菜单一并刷新（首次进入或缓存缺失时）
    if (!localStorage.getItem('perms')) {
      const perms = await getMyPerms()
      localStorage.setItem('perms', JSON.stringify(perms))
    }
  } catch (e) {
    // 菜单加载失败时保留原缓存；401 已由拦截器处理
    ElMessage.warning('菜单加载失败，请刷新重试')
  }
}

onMounted(() => {
  if (!menus.value.length) loadMenus()
  setupAlarmNotify()
})

// ===== T-35 全局告警提醒：声音 + 弹窗 + 角标 =====
const alarmCount = ref(0)
const goAlarm = () => {
  alarmCount.value = 0
  router.push('/alarm')
}

/** 提示音（Web Audio 生成蜂鸣，无需音频文件） */
const playBeep = () => {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.type = 'sine'
    osc.frequency.value = 880
    gain.gain.setValueAtTime(0.25, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5)
    osc.start()
    osc.stop(ctx.currentTime + 0.5)
  } catch (e) { /* 浏览器限制时静默 */ }
}

const setupAlarmNotify = () => {
  wsClient.subscribeAlarm(alarm => {
    alarmCount.value++
    playBeep()
    const typeMap = { 1: 'warning', 2: 'error', 3: 'error' }
    ElNotification({
      title: '新告警提醒',
      message: alarm.alarmContent || '检测到新告警',
      type: typeMap[alarm.alarmLevel] || 'warning',
      duration: 6000,
      onClick: goAlarm
    })
  })
}

const handleCommand = command => {
  if (command === 'changePwd') {
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    pwdVisible.value = true
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
      .then(() => doLogout())
      .catch(() => {})
  }
}

const doLogout = async () => {
  try {
    await logout() // T-34 删除 Redis 会话（服务端失效）
  } catch (e) { /* 忽略，本地照常清理 */ }
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('menus')
  localStorage.removeItem('perms')
  localStorage.removeItem('lastLogin')
  router.push('/login')
}

// ===== 重置密码（当前登录用户自助改密，数据库实时更新） =====
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const submitChangePwd = async () => {
  const f = pwdForm.value
  if (!f.oldPassword) return ElMessage.warning('请输入原密码')
  if (!f.newPassword || f.newPassword.length < 6) return ElMessage.warning('新密码长度不能少于 6 位')
  if (f.newPassword !== f.confirmPassword) return ElMessage.warning('两次输入的新密码不一致')
  pwdSubmitting.value = true
  try {
    await changeMyPassword({ oldPassword: f.oldPassword, newPassword: f.newPassword })
    ElMessage.success('密码已重置并保存，请使用新密码重新登录')
    pwdVisible.value = false
    doLogout()
  } finally {
    pwdSubmitting.value = false
  }
}
</script>

<style scoped>
.layout { height: 100%; }
.aside { background-color: #001529; overflow-x: hidden; }
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #ffffff1a;
}
.aside :deep(.el-menu) { border-right: none; }
.header {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-title { font-size: 16px; font-weight: 600; color: #303133; }
.header-right { display: flex; align-items: center; gap: 12px; }
.alarm-badge { cursor: pointer; display: inline-flex; align-items: center; }
.alarm-icon { color: #606266; }
.alarm-icon:hover { color: #409EFF; }
.user-name {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #303133;
  outline: none;
}
.main { background: #f0f2f5; }

/* ===== 右下角小机器人悬浮球 ===== */
.robot-ball {
  position: fixed;
  right: 26px;
  bottom: 30px;
  z-index: 2001;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #6f5bff);
  box-shadow: 0 6px 18px rgba(64, 158, 255, .45);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform .25s, box-shadow .25s;
  user-select: none;
}
.robot-ball:hover { transform: scale(1.1) rotate(-6deg); box-shadow: 0 8px 24px rgba(64, 158, 255, .6); }
.robot-face { font-size: 30px; line-height: 1; animation: robot-float 2.4s ease-in-out infinite; }
.robot-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #67c23a;
  border: 2px solid #fff;
  animation: badge-pulse 1.6s infinite;
}
@keyframes robot-float { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
@keyframes badge-pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(103, 194, 58, .5); } 50% { box-shadow: 0 0 0 6px rgba(103, 194, 58, 0); } }
.robot-drawer :deep(.el-drawer__body) { padding: 0; }
.robot-drawer :deep(.el-drawer__header) { margin-bottom: 0; }
</style>
