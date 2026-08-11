<template>
  <div>
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb16">
      <el-col :span="6" v-for="item in statCards" :key="item.label">
        <el-card shadow="never" class="stat-card" :style="{ borderTopColor: item.color }">
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><Bell /></el-icon> 告警列表</span>
          <div>
            <el-select v-model="filters.alarmLevel" placeholder="告警级别" clearable style="width: 120px" @change="loadList">
              <el-option label="预警" :value="1" />
              <el-option label="警报" :value="2" />
              <el-option label="控制" :value="3" />
            </el-select>
            <el-select v-model="filters.alarmSource" placeholder="告警来源" clearable style="width: 130px; margin-left: 8px" @change="loadList">
              <el-option label="设备监测" :value="1" />
              <el-option label="环境监测" :value="2" />
              <el-option label="AI识别" :value="3" />
            </el-select>
            <el-select v-model="filters.handleStatus" placeholder="处置状态" clearable style="width: 120px; margin-left: 8px" @change="loadList">
              <el-option label="未处置" :value="0" />
              <el-option label="处置中" :value="1" />
              <el-option label="已处置" :value="2" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="alarmNo" label="告警编号" width="170" />
        <el-table-column prop="alarmTime" label="告警时间" width="165" />
        <el-table-column prop="alarmContent" label="告警内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="来源" width="95" align="center">
          <template #default="{ row }">
            {{ sourceMap[row.alarmSource] || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="级别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.alarmLevel)" size="small">{{ levelMap[row.alarmLevel] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发值" width="100" align="center">
          <template #default="{ row }">{{ row.alarmValue }}</template>
        </el-table-column>
        <el-table-column label="处置状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.handleStatus)" size="small">{{ statusMap[row.handleStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="165" align="center">
          <template #default="{ row }">
            <el-button v-if="row.batchNo" type="warning" size="small" @click="openRelated(row)">关联</el-button>
            <el-button v-if="row.handleStatus !== 2" type="primary" size="small" v-permission="'sys:alarm:handle'" @click="openHandle(row)">处置</el-button>
            <el-button v-else type="info" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="mt12" background layout="total, prev, pager, next" :total="total"
        :page-size="filters.pageSize" v-model:current-page="filters.pageNum" @current-change="loadList" />
    </el-card>

    <!-- 关联告警弹窗（同一次事件 batchNo） -->
    <el-dialog v-model="relatedVisible" title="关联告警（同一次事件触发）" width="760px">
      <el-alert v-if="relatedBatchNo" type="info" :closable="false" class="mb12"
        :title="'事件批次号：' + relatedBatchNo + '，共 ' + relatedList.length + ' 条关联告警'" />
      <el-table :data="relatedList" v-loading="relatedLoading" size="small" border stripe max-height="380">
        <el-table-column prop="alarmNo" label="告警编号" width="165" />
        <el-table-column prop="alarmTime" label="触发时间" width="160" />
        <el-table-column prop="alarmContent" label="告警内容" min-width="220" show-overflow-tooltip />
        <el-table-column label="级别" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.alarmLevel)" size="small">{{ levelMap[row.alarmLevel] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发值" width="80" align="center">
          <template #default="{ row }">{{ row.alarmValue }}</template>
        </el-table-column>
        <el-table-column label="处置状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.handleStatus)" size="small">{{ statusMap[row.handleStatus] }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 处置弹窗 -->
    <el-dialog v-model="handleVisible" title="告警处置" width="480px">
      <el-form :model="handleForm" label-width="80px">
        <el-form-item label="告警内容">
          <el-input :model-value="current && current.alarmContent" disabled type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="处置人">
          <el-input v-model="handleForm.handlePerson" placeholder="请输入处置人" />
        </el-form-item>
        <el-form-item label="处置措施">
          <el-input v-model="handleForm.handleMeasure" type="textarea" :rows="2" placeholder="请输入处置措施" />
        </el-form-item>
        <el-form-item label="处置结论">
          <el-input v-model="handleForm.handleConclusion" type="textarea" :rows="2" placeholder="请输入处置结论" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确认处置</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="告警详情" width="520px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="告警编号">{{ current.alarmNo }}</el-descriptions-item>
        <el-descriptions-item label="告警时间">{{ current.alarmTime }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ sourceMap[current.alarmSource] }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="levelTag(current.alarmLevel)" size="small">{{ levelMap[current.alarmLevel] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="触发值">{{ current.alarmValue }}</el-descriptions-item>
        <el-descriptions-item label="处置人">{{ current.handlePerson }}</el-descriptions-item>
        <el-descriptions-item label="处置措施" :span="2">{{ current.handleMeasure }}</el-descriptions-item>
        <el-descriptions-item label="处置结论" :span="2">{{ current.handleConclusion }}</el-descriptions-item>
        <el-descriptions-item label="处置时间" :span="2">{{ current.handleTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlarmList, handleAlarm, getAlarmStats, getRelatedAlarms } from '../api/monitor'
import wsClient from '../api/ws'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const stats = ref({ byStatus: [], byLevel: [] })

const filters = ref({ pageNum: 1, pageSize: 10, alarmLevel: null, alarmSource: null, handleStatus: null })

const sourceMap = { 1: '设备监测', 2: '环境监测', 3: 'AI识别' }
const levelMap = { 1: '预警', 2: '警报', 3: '控制' }
const statusMap = { 0: '未处置', 1: '处置中', 2: '已处置' }
const levelTag = l => ['', 'warning', 'danger', 'error'][l] || 'info'
const statusTag = s => ['danger', 'warning', 'success'][s] || 'info'

const statCards = computed(() => {
  const byStatus = stats.value.byStatus || []
  const get = key => {
    const item = byStatus.find(i => Number(i.status) === key)
    return item ? Number(item.cnt) : 0
  }
  const totalAlarms = byStatus.reduce((sum, i) => sum + Number(i.cnt), 0)
  return [
    { label: '告警总数', value: totalAlarms, color: '#409EFF' },
    { label: '未处置', value: get(0), color: '#F56C6C' },
    { label: '处置中', value: get(1), color: '#E6A23C' },
    { label: '已处置', value: get(2), color: '#67C23A' }
  ]
})

const loadList = async () => {
  loading.value = true
  try {
    const data = await getAlarmList(filters.value)
    list.value = data.records
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  stats.value = await getAlarmStats()
}

// 处置
const handleVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const handleForm = ref({ handlePerson: '', handleMeasure: '', handleConclusion: '' })

const openHandle = row => {
  current.value = row
  handleForm.value = { handlePerson: '', handleMeasure: '', handleConclusion: '' }
  handleVisible.value = true
}

const submitHandle = async () => {
  submitting.value = true
  try {
    await handleAlarm(current.value.id, handleForm.value)
    ElMessage.success('处置成功')
    handleVisible.value = false
    loadList()
    loadStats()
  } finally {
    submitting.value = false
  }
}

const detailVisible = ref(false)
const openDetail = row => {
  current.value = row
  detailVisible.value = true
}

// 关联告警（同一次事件 batchNo）
const relatedVisible = ref(false)
const relatedLoading = ref(false)
const relatedList = ref([])
const relatedBatchNo = ref('')

const openRelated = async row => {
  current.value = row
  relatedVisible.value = true
  relatedLoading.value = true
  try {
    const data = await getRelatedAlarms(row.id)
    relatedList.value = data
    relatedBatchNo.value = (data[0] && data[0].batchNo) || ''
  } finally {
    relatedLoading.value = false
  }
}

let unsub = null
onMounted(async () => {
  loadList()
  loadStats()
  unsub = wsClient.subscribe(() => {
    // 有新数据推送时刷新统计（列表由用户手动翻页）
    loadStats()
  })
})
onUnmounted(() => unsub && unsub())
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.mt12 { margin-top: 12px; }
.stat-card { text-align: center; border-top: 3px solid; }
.stat-value { font-size: 30px; font-weight: 700; }
.stat-label { color: #606266; margin-top: 4px; font-size: 14px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
