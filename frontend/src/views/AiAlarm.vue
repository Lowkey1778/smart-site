<template>
  <div>
    <el-alert type="info" :closable="false" class="mb16"
      title="AI 智能识别：视频帧 → AI 推理服务 → 危险行为告警（安全帽/安全服/吸烟/明火）。截图来自视频流实时截帧" />

    <!-- 统计 -->
    <el-row :gutter="16" class="mb16">
      <el-col :span="6" v-for="item in statCards" :key="item.label">
        <el-card shadow="never" class="stat-card" :style="{ borderTopColor: item.color }">
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><Aim /></el-icon> AI 告警列表</span>
          <div>
            <el-select v-model="filters.keyword" placeholder="告警类型" clearable style="width: 150px" @change="loadList">
              <el-option label="安全帽未佩戴" value="安全帽未佩戴" />
              <el-option label="安全服未穿" value="安全服未穿" />
              <el-option label="现场吸烟" value="现场吸烟" />
              <el-option label="明火" value="明火" />
            </el-select>
            <el-select v-model="filters.handleStatus" placeholder="处置状态" clearable style="width: 120px; margin-left: 8px" @change="loadList">
              <el-option label="未处置" :value="0" />
              <el-option label="处置中" :value="1" />
              <el-option label="已处置" :value="2" />
            </el-select>
            <el-button size="small" class="ml12" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 告警卡片 -->
      <div v-loading="loading" class="card-list">
        <el-empty v-if="!list.length && !loading" description="暂无 AI 告警" />
        <el-card v-for="a in list" :key="a.id" shadow="hover" class="alarm-card" :class="'lv-' + a.alarmLevel" @click="openDetail(a)">
          <div class="alarm-row">
            <el-image :src="a.imageUrl" fit="cover" class="thumb" :preview-src-list="[a.imageUrl]" preview-teleported>
              <template #error>
                <div class="thumb-err"><el-icon><Picture /></el-icon></div>
              </template>
            </el-image>
            <div class="alarm-info">
              <div class="info-top">
                <el-tag :type="typeTag(a.alarmContent)" size="small">{{ typeOf(a.alarmContent) }}</el-tag>
                <el-tag :type="levelTag(a.alarmLevel)" size="small" class="ml8">{{ levelMap[a.alarmLevel] }}</el-tag>
                <el-tag :type="statusTag(a.handleStatus)" size="small" class="ml8">{{ statusMap[a.handleStatus] }}</el-tag>
              </div>
              <div class="info-content">{{ a.alarmContent }}</div>
              <div class="info-time">{{ a.alarmTime }}</div>
            </div>
            <div class="alarm-actions">
              <el-button v-if="a.handleStatus === 0" type="primary" size="small" v-permission="'sys:ai:handle'" @click.stop="openHandle(a)">开始处置</el-button>
              <el-button v-else-if="a.handleStatus === 1" type="warning" size="small" v-permission="'sys:ai:handle'" @click.stop="openFinish(a)">完成处置</el-button>
              <el-button v-else type="info" size="small" @click.stop="openDetail(a)">详情</el-button>
            </div>
          </div>
        </el-card>
      </div>

      <el-pagination class="mt12" background layout="total, prev, pager, next" :total="total"
        :page-size="filters.pageSize" v-model:current-page="filters.pageNum" @current-change="loadList" />
    </el-card>

    <!-- 处置弹窗 -->
    <el-dialog v-model="handleVisible" :title="handleMode === 'start' ? '开始处置' : '完成处置'" width="640px">
      <div class="handle-body">
        <el-image :src="current.imageUrl" fit="contain" class="handle-img" :preview-src-list="[current.imageUrl]" preview-teleported />
        <el-form :model="handleForm" label-width="80px" class="handle-form">
          <el-form-item label="告警内容">
            <el-input :model-value="current.alarmContent" disabled type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="处置人" required>
            <el-input v-model="handleForm.handlePerson" placeholder="请输入处置人" />
          </el-form-item>
          <template v-if="handleMode === 'finish'">
            <el-form-item label="处置措施">
              <el-input v-model="handleForm.handleMeasure" type="textarea" :rows="2" placeholder="如：通知安全员现场核查" />
            </el-form-item>
            <el-form-item label="处置结论">
              <el-input v-model="handleForm.handleConclusion" type="textarea" :rows="2" placeholder="处置结果说明" />
            </el-form-item>
          </template>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submitHandle">
              {{ handleMode === 'start' ? '开始处置' : '确认完成' }}
            </el-button>
          </el-form-item>
          <el-alert v-if="handleMode === 'start'" type="info" :closable="false"
            title="开始处置后告警进入「处置中」状态，请在完成后再次点击「完成处置」提交处置结果。" />
        </el-form>
      </div>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="'告警详情 - ' + typeOf(current.alarmContent)" width="560px">
      <el-image :src="current.imageUrl" fit="contain" class="detail-img" :preview-src-list="[current.imageUrl]" preview-teleported />
      <el-descriptions :column="2" border class="mt12">
        <el-descriptions-item label="告警编号">{{ current.alarmNo }}</el-descriptions-item>
        <el-descriptions-item label="告警时间">{{ current.alarmTime }}</el-descriptions-item>
        <el-descriptions-item label="级别">{{ levelMap[current.alarmLevel] }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusMap[current.handleStatus] }}</el-descriptions-item>
        <el-descriptions-item label="处置人">{{ current.handlePerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置时间">{{ current.handleTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置措施" :span="2">{{ current.handleMeasure || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置结论" :span="2">{{ current.handleConclusion || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlarmList, handleAlarm, startHandleAlarm } from '../api/monitor'
import wsClient from '../api/ws'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const stats = ref({ unhandled: 0, handled: 0, total: 0 })

const filters = ref({ pageNum: 1, pageSize: 8, alarmSource: 3, keyword: null, handleStatus: null })

const levelMap = { 1: '预警', 2: '警报', 3: '控制' }
const statusMap = { 0: '未处置', 1: '处置中', 2: '已处置' }
const levelTag = l => ['', 'warning', 'danger', 'error'][l] || 'info'
const statusTag = s => ['danger', 'warning', 'success'][s] || 'info'

const typeOf = content => {
  const map = [['安全帽未佩戴', 'warning'], ['安全服未穿', 'danger'], ['现场吸烟', 'warning'], ['明火', 'error']]
  for (const [name] of map) {
    if (content && content.includes(name)) return name
  }
  return '未知'
}
const typeTag = content => {
  if (content && content.includes('明火')) return 'error'
  if (content && content.includes('安全服')) return 'danger'
  return 'warning'
}

const statCards = computed(() => [
  { label: 'AI 告警总数', value: stats.value.total, color: '#409EFF' },
  { label: '未处置', value: stats.value.unhandled, color: '#F56C6C' },
  { label: '处置中', value: stats.value.handling || 0, color: '#E6A23C' },
  { label: '已处置', value: stats.value.handled, color: '#67C23A' }
])

const loadList = async () => {
  loading.value = true
  try {
    const data = await getAlarmList(filters.value)
    list.value = data.records
    total.value = Number(data.total)
    // 统计
    const all = await getAlarmList({ pageNum: 1, pageSize: 1, alarmSource: 3 })
    stats.value.total = Number(all.total)
    const un = await getAlarmList({ pageNum: 1, pageSize: 1, alarmSource: 3, handleStatus: 0 })
    stats.value.unhandled = Number(un.total)
    const hg = await getAlarmList({ pageNum: 1, pageSize: 1, alarmSource: 3, handleStatus: 1 })
    stats.value.handling = Number(hg.total)
    const hd = await getAlarmList({ pageNum: 1, pageSize: 1, alarmSource: 3, handleStatus: 2 })
    stats.value.handled = Number(hd.total)
  } finally {
    loading.value = false
  }
}

// 处置
const handleVisible = ref(false)
const detailVisible = ref(false)
const submitting = ref(false)
const current = ref({})
const handleForm = ref({ handlePerson: '', handleMeasure: '', handleConclusion: '' })

const handleMode = ref('start') // start-开始处置 / finish-完成处置
const openHandle = row => {
  current.value = row
  handleMode.value = 'start'
  handleForm.value = { handlePerson: '', handleMeasure: '', handleConclusion: '' }
  handleVisible.value = true
}
const openFinish = row => {
  current.value = row
  handleMode.value = 'finish'
  handleForm.value = { handlePerson: row.handlePerson || '', handleMeasure: '', handleConclusion: '' }
  handleVisible.value = true
}
const openDetail = row => {
  current.value = row
  detailVisible.value = true
}
const submitHandle = async () => {
  if (!handleForm.value.handlePerson) {
    ElMessage.warning('请填写处置人')
    return
  }
  submitting.value = true
  try {
    if (handleMode.value === 'start') {
      await startHandleAlarm(current.value.id, { handlePerson: handleForm.value.handlePerson })
      ElMessage.success('已开始处置，状态更新为处置中')
    } else {
      await handleAlarm(current.value.id, handleForm.value)
      ElMessage.success('处置完成')
    }
    handleVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

let unsub = null
let timer = null
onMounted(async () => {
  await loadList()
  unsub = wsClient.subscribe(data => {
    if (data.aiAlarm) loadList()
  })
  timer = setInterval(loadList, 30000)
})
onUnmounted(() => {
  unsub && unsub()
  timer && clearInterval(timer)
})
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.ml8 { margin-left: 8px; }
.ml12 { margin-left: 12px; }
.mt12 { margin-top: 12px; }
.stat-card { text-align: center; border-top: 3px solid; }
.stat-value { font-size: 30px; font-weight: 700; }
.stat-label { color: #606266; margin-top: 4px; font-size: 14px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-list { min-height: 120px; }
.alarm-card { margin-bottom: 10px; cursor: pointer; border-left: 4px solid #409EFF; }
.alarm-card.lv-1 { border-left-color: #E6A23C; }
.alarm-card.lv-2 { border-left-color: #F56C6C; }
.alarm-card.lv-3 { border-left-color: #b71c1c; }
.alarm-row { display: flex; align-items: center; gap: 14px; }
.thumb { width: 180px; height: 100px; border-radius: 4px; flex-shrink: 0; }
.thumb-err { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #f0f2f5; color: #909399; }
.alarm-info { flex: 1; min-width: 0; }
.info-top { display: flex; align-items: center; }
.info-content { margin-top: 8px; font-size: 14px; color: #303133; font-weight: 500; }
.info-time { margin-top: 4px; font-size: 12px; color: #909399; }
.alarm-actions { flex-shrink: 0; }
.handle-body { display: flex; gap: 16px; }
.handle-img { width: 280px; height: 160px; border-radius: 4px; flex-shrink: 0; background: #f0f2f5; }
.handle-form { flex: 1; }
.detail-img { width: 100%; height: 220px; border-radius: 4px; background: #f0f2f5; }
</style>
