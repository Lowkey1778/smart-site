<template>
  <div class="coze-page" :class="{ embedded }">
    <div class="chat-panel">
      <!-- 头部 -->
      <div class="chat-header">
        <div class="header-left">
          <el-icon :size="22" color="#409EFF"><ChatDotRound /></el-icon>
          <span class="title">Coze 智能体 · 智慧工地安全助手</span>
        </div>
        <div class="header-right">
          <el-tag size="small" :type="sourceTagType" class="engine-tag">{{ engineLabel }}</el-tag>
          <el-button v-if="messages.length" size="small" plain @click="clearChat">
            <el-icon><Delete /></el-icon>&nbsp;清空对话
          </el-button>
          <el-button v-if="isAdmin" size="small" type="primary" plain @click="openConfig">
            <el-icon><Setting /></el-icon>&nbsp;接入配置
          </el-button>
        </div>
      </div>

      <!-- 消息区 -->
      <div ref="msgBox" class="msg-area" @scroll="onScroll">
        <div v-if="!messages.length" class="empty-tip">
          <el-icon :size="40" color="#c0c4cc"><MagicStick /></el-icon>
          <p>您好！我是智慧工地安全助手，可以为您分析安全态势、<br />查询告警与设备状态、提供安全建议。</p>
          <p class="tip-sub">试试点击右侧快捷问题，或直接输入您的问题</p>
        </div>
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="avatar" :class="m.role">
            <el-icon v-if="m.role === 'user'"><User /></el-icon>
            <el-icon v-else><MagicStick /></el-icon>
          </div>
          <div class="bubble" :class="m.role">
            <div v-if="m.loading" class="loading">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
            <div v-else class="content">{{ m.content }}</div>
            <div v-if="m.engine && m.role === 'assistant'" class="engine-line">{{ m.engine }}</div>
          </div>
        </div>
      </div>

      <!-- 快捷问题 -->
      <div class="quick-bar" v-if="quickQuestions.length">
        <span class="quick-label">快捷问题：</span>
        <el-tag
          v-for="(q, i) in quickQuestions"
          :key="i"
          class="quick-tag"
          effect="plain"
          @click="send(q)"
        >{{ q }}</el-tag>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="请输入您的问题，如：今日安全态势如何？"
          @keydown.enter.exact.prevent="send(input)"
        />
        <el-button type="primary" :loading="sending" @click="send(input)">
          <el-icon v-if="!sending"><Promotion /></el-icon>&nbsp;发送
        </el-button>
      </div>
    </div>

    <!-- 接入配置弹窗（仅管理员）：填写 Coze Token / Bot ID 接入真实智能体 -->
    <el-dialog v-model="configVisible" title="Coze 智能体接入配置" width="560px" destroy-on-close>
      <el-alert type="info" :closable="false" class="mb12"
        title="前往 Coze 开放平台（www.coze.cn/open）创建智能体，获取 API Token 与 Bot ID 填入即可接入真实大模型；留空保存则使用本地知识引擎（基于系统真实数据回答）。" />
      <el-form :model="configForm" label-width="110px">
        <el-form-item label="API Token" required>
          <el-input v-model="configForm.apiToken" type="password" show-password
            :placeholder="configForm.configured ? (configForm.apiTokenMasked || '已配置，留空保持不变') : 'pat_ 开头，如 pat_xxxxxxxx'" />
        </el-form-item>
        <el-form-item label="Bot ID" required>
          <el-input v-model="configForm.botId" placeholder="如 7460xxxxxxxxxxxx" />
        </el-form-item>
        <el-form-item label="接口地址">
          <el-select v-model="configForm.baseUrl" style="width: 100%">
            <el-option label="国内版 https://api.coze.cn" value="https://api.coze.cn" />
            <el-option label="海外版 https://api.coze.com" value="https://api.coze.com" />
          </el-select>
        </el-form-item>
        <el-form-item label="接入状态">
          <el-tag :type="configForm.configured ? 'success' : 'info'" size="small">
            {{ configForm.configured ? '已接入真实 Coze（' + (configForm.botId || '') + '）' : '未接入，使用本地知识引擎' }}
          </el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="warning" plain :loading="savingConfig" @click="clearConfig">清除接入</el-button>
        <el-button type="primary" :loading="savingConfig" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { cozeChat, getQuickQuestions, getCozeConfig, saveCozeConfig } from '../api/coze'

// embedded：嵌入右下角抽屉时铺满容器（去除页面级边距/高度计算）
const props = defineProps({
  embedded: { type: Boolean, default: false }
})

const HISTORY_KEY = 'coze_chat_history'
const messages = ref([])
const input = ref('')
const sending = ref(false)
const quickQuestions = ref([])
const msgBox = ref(null)
const lastEngine = ref('')

// 仅管理员显示"接入配置"入口
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
const isAdmin = (userInfo.roles || []).includes('ADMIN')

const sourceTagType = computed(() => (lastEngine.value.includes('Coze 智能体') ? 'success' : 'info'))
const engineLabel = computed(() => {
  if (!lastEngine.value) return '等待提问'
  return lastEngine.value.includes('Coze 智能体') ? '已接入 Coze' : '本地知识引擎'
})

const scrollToBottom = () => {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

const onScroll = () => { /* 自动滚动由 scrollToBottom 控制 */ }

/** 保存聊天记录到浏览器本地（重新打开网站自动恢复，无需重新接入） */
const saveHistory = () => {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(messages.value.slice(-60)))
  } catch (e) { /* 存储满时忽略 */ }
}

const send = async (text) => {
  const content = (text || '').trim()
  if (!content || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content })
  messages.value.push({ role: 'assistant', content: '', loading: true })
  sending.value = true
  saveHistory()
  scrollToBottom()
  try {
    // 注意：request 拦截器已解包 Result.data，这里直接取 res.reply / res.engine
    const res = await cozeChat({ message: content })
    const last = messages.value[messages.value.length - 1]
    last.loading = false
    last.content = res.reply
    last.engine = res.engine
    lastEngine.value = res.engine || ''
    scrollToBottom()
  } catch (e) {
    const last = messages.value[messages.value.length - 1]
    last.loading = false
    last.content = '抱歉，智能体服务暂时不可用，请稍后重试。'
    ElMessage.error('智能体调用失败')
  } finally {
    sending.value = false
    saveHistory()
    scrollToBottom()
  }
}

const clearChat = () => {
  messages.value = []
  lastEngine.value = ''
  localStorage.removeItem(HISTORY_KEY)
  ElMessage.success('已清空对话记录')
}

/* ===== 接入配置（仅管理员） ===== */
const configVisible = ref(false)
const savingConfig = ref(false)
const configForm = ref({ apiToken: '', botId: '', baseUrl: 'https://api.coze.cn', configured: false, apiTokenMasked: '' })

const openConfig = async () => {
  configVisible.value = true
  configForm.value = { apiToken: '', botId: '', baseUrl: 'https://api.coze.cn', configured: false, apiTokenMasked: '' }
  try {
    const cfg = await getCozeConfig()
    configForm.value = {
      apiToken: '',
      botId: cfg.botId || '',
      baseUrl: cfg.baseUrl || 'https://api.coze.cn',
      configured: !!cfg.configured,
      apiTokenMasked: cfg.apiTokenMasked || ''
    }
  } catch (e) { /* 忽略 */ }
}

const saveConfig = async () => {
  const f = configForm.value
  if (!f.apiToken && !f.configured) return ElMessage.warning('请填写 API Token')
  if (!f.botId) return ElMessage.warning('请填写 Bot ID')
  savingConfig.value = true
  try {
    await saveCozeConfig({ apiToken: f.apiToken, botId: f.botId, baseUrl: f.baseUrl })
    ElMessage.success('配置已保存' + (f.apiToken ? '，已接入真实 Coze 智能体' : ''))
    configVisible.value = false
    lastEngine.value = f.apiToken ? 'Coze 智能体' : '本地知识引擎'
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingConfig.value = false
  }
}

const clearConfig = async () => {
  savingConfig.value = true
  try {
    await saveCozeConfig({ apiToken: '', botId: '', baseUrl: 'https://api.coze.cn' })
    ElMessage.success('已清除接入，恢复本地知识引擎')
    configVisible.value = false
    lastEngine.value = '本地知识引擎'
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    savingConfig.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getQuickQuestions()
    quickQuestions.value = res || []
  } catch (e) { /* 忽略 */ }
  // 恢复上次会话记录：重新打开网站不再重新接入，历史对话直接呈现
  let restored = false
  try {
    const saved = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    if (Array.isArray(saved) && saved.length) {
      messages.value = saved.filter(m => m && m.content !== undefined)
      const lastAsst = [...messages.value].reverse().find(m => m.role === 'assistant' && m.engine)
      if (lastAsst) lastEngine.value = lastAsst.engine
      restored = true
    }
  } catch (e) { /* 忽略 */ }
  if (!restored) {
    messages.value.push({
      role: 'assistant',
      content: '您好！我是智慧工地安全助手 🤖\n可以为您分析安全态势、查询告警与设备状态、提供安全建议。\n（未接入 Coze 时使用本地知识引擎，基于系统真实数据回答；管理员可点击右上角「接入配置」填写 Token/Bot ID 接入真实智能体）',
      engine: '本地知识引擎'
    })
    lastEngine.value = '本地知识引擎'
  }
  scrollToBottom()
})
</script>

<style scoped>
.coze-page { height: calc(100vh - 140px); display: flex; justify-content: center; padding: 10px; }
/* 嵌入抽屉模式：铺满抽屉内容区 */
.coze-page.embedded { height: 100%; padding: 0; }
.chat-panel {
  width: 860px; max-width: 100%; background: #fff; border-radius: 10px;
  box-shadow: 0 2px 12px rgba(0,0,0,.08); display: flex; flex-direction: column; overflow: hidden;
}
.chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-bottom: 1px solid #f0f0f0;
}
.header-left { display: flex; align-items: center; gap: 8px; }
.header-right { display: flex; align-items: center; gap: 10px; }
.mb12 { margin-bottom: 12px; }
.title { font-size: 15px; font-weight: 600; color: #303133; }
.msg-area { flex: 1; overflow-y: auto; padding: 20px; background: #fafbfc; }
.empty-tip { text-align: center; color: #909399; margin-top: 80px; font-size: 14px; line-height: 1.9; }
.tip-sub { font-size: 12px; color: #c0c4cc; }
.msg-row { display: flex; margin-bottom: 16px; gap: 10px; }
.msg-row.user { flex-direction: row-reverse; }
.avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; color: #fff;
}
.avatar.user { background: #409EFF; }
.avatar.assistant { background: #67c23a; }
.bubble {
  max-width: 68%; padding: 10px 14px; border-radius: 10px; font-size: 14px; line-height: 1.7; white-space: pre-wrap; word-break: break-word;
}
.bubble.user { background: #409EFF; color: #fff; border-top-right-radius: 2px; }
.bubble.assistant { background: #fff; color: #303133; border: 1px solid #ebeef5; border-top-left-radius: 2px; }
.engine-line { margin-top: 6px; font-size: 11px; color: #c0c4cc; }
.loading { display: flex; gap: 5px; padding: 4px 0; }
.dot {
  width: 8px; height: 8px; border-radius: 50%; background: #c0c4cc;
  animation: blink 1.2s infinite;
}
.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%, 80%, 100% { opacity: .2; } 40% { opacity: 1; } }
.quick-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 10px 20px; border-top: 1px solid #f0f0f0; background: #fff;
}
.quick-label { font-size: 12px; color: #909399; }
.quick-tag { cursor: pointer; }
.input-area { display: flex; gap: 10px; padding: 12px 20px 16px; border-top: 1px solid #f0f0f0; align-items: flex-end; }
.input-area .el-button { height: 52px; }
</style>
