<template>
  <div>
    <el-alert type="info" :closable="false" class="mb16"
      title="环境监测：PM2.5 / PM10 / 噪声 / 温度 / 湿度 / 风速，按位置分组展示，点击监测点查看日统计" />

    <!-- 顶部操作栏 -->
    <div class="mb16 toolbar">
      <el-button type="primary" v-permission="'sys:env:point'" @click="openManage">
        <el-icon><Setting /></el-icon>&nbsp;监测点管理
      </el-button>
    </div>

    <!-- 按位置分组展示监测点（一期项目 / 1号楼 / 2号楼 / 东侧 / 西侧 / 南侧） -->
    <div v-for="g in groupedPoints" :key="g.name" class="mb24">
      <div class="loc-title">
        <el-icon><Location /></el-icon> {{ g.name }}
        <el-tag size="small" type="info" effect="plain">{{ g.items.length }} 个监测点</el-tag>
      </div>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" v-for="p in g.items" :key="p.pointId">
          <el-card shadow="hover" class="env-card" :class="[statusClass(p), { 'is-selected': isSelected(p) }]" @click="selectPoint(p)">
            <div class="env-head">
              <span class="env-name">{{ p.pointName }}</span>
              <el-tag :type="statusTag(p)" size="small">{{ statusText(p) }}</el-tag>
            </div>
            <div class="env-value" :style="{ color: statusColor(p) }">
              {{ fmt(p.value) }} <span class="unit">{{ p.unit }}</span>
            </div>
            <div class="env-sub">
              预警阈值 {{ fmt(p.warnMax) }} · 报警阈值 {{ fmt(p.alarmMax) }}
              <template v-if="p.warnMin != null"> · 下限 {{ fmt(p.warnMin) }}</template>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <div v-if="!g.items.length" class="empty-tip">该位置暂未配置监测点</div>
    </div>

    <!-- T-23 日统计卡片 -->
    <el-card shadow="never" class="mt16">
      <template #header>
        <div class="card-header">
          <span><el-icon><Calendar /></el-icon> 日统计：{{ selected ? selected.pointName : '请点击上方监测点' }}（近 7 天）</span>
        </div>
      </template>
      <el-row :gutter="16" v-if="todayStat">
        <el-col :span="8">
          <div class="stat-card"><div class="stat-value" style="color:#E6A23C">{{ todayStat.maxValue }}</div><div class="stat-label">今日最大</div></div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card"><div class="stat-value" style="color:#67C23A">{{ todayStat.minValue }}</div><div class="stat-label">今日最小</div></div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card"><div class="stat-value" style="color:#409EFF">{{ todayStat.avgValue }}</div><div class="stat-label">今日平均</div></div>
        </el-col>
      </el-row>
      <el-table :data="dailyStats" size="small" stripe class="mt12">
        <el-table-column prop="statDate" label="日期" width="150" />
        <el-table-column prop="maxValue" label="最大值" />
        <el-table-column prop="minValue" label="最小值" />
        <el-table-column prop="avgValue" label="平均值" />
        <el-table-column prop="dataCount" label="数据条数" width="110" align="center" />
      </el-table>
      <div v-if="!dailyStats.length" class="empty-tip">暂无统计数据</div>
    </el-card>

    <!-- T-22 监测点管理弹窗 -->
    <el-dialog v-model="manageVisible" title="环境监测点管理" width="920px">
      <el-form :inline="true" class="mb12">
        <el-form-item label="名称"><el-input v-model="form.pointName" placeholder="如 扬尘监测点" style="width: 130px" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.pointCode" placeholder="如 ENV-PM25-02" style="width: 140px" :disabled="!!editingId" /></el-form-item>
        <el-form-item label="设备">
          <el-select v-model="form.deviceId" placeholder="所属设备" style="width: 150px">
            <el-option v-for="d in devices" :key="d.id" :label="d.deviceName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="子类型">
          <el-select v-model="form.monitorSubType" placeholder="监测子类型" style="width: 110px" @change="onSubTypeChange">
            <el-option v-for="s in subTypes" :key="s.name" :label="s.name" :value="s.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" placeholder="自动带出" style="width: 80px" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <el-row :gutter="8" class="mb12">
        <el-col :span="6"><el-input v-model="form.warnMin" placeholder="预警下限" /></el-col>
        <el-col :span="6"><el-input v-model="form.warnMax" placeholder="预警上限" /></el-col>
        <el-col :span="6"><el-input v-model="form.alarmMin" placeholder="报警下限" /></el-col>
        <el-col :span="6"><el-input v-model="form.alarmMax" placeholder="报警上限" /></el-col>
      </el-row>
      <el-button type="primary" class="mb12" @click="submitPoint">{{ editingId ? '保存修改' : '新增监测点' }}</el-button>
      <el-button class="mb12" @click="resetForm">清空</el-button>

      <el-table :data="points" size="small" border stripe max-height="300">
        <el-table-column prop="pointCode" label="编码" width="120" />
        <el-table-column prop="pointName" label="名称" width="130" />
        <el-table-column prop="monitorSubType" label="子类型" width="90" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="预警范围" width="130">
          <template #default="{ row }">{{ fmt(row.warnMin) }} ~ {{ fmt(row.warnMax) }}</template>
        </el-table-column>
        <el-table-column label="报警范围" width="130">
          <template #default="{ row }">{{ fmt(row.alarmMin) }} ~ {{ fmt(row.alarmMax) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removePoint(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEnvPoints, addEnvPoint, updateEnvPoint, deleteEnvPoint, getEnvDailyStats } from '../api/monitor'
import { getDevicePage } from '../api/device'
import wsClient from '../api/ws'

const points = ref([])
const selected = ref(null)

const fmt = v => (v === null || v === undefined || v === '' ? '-' : Number(v).toLocaleString())

const statusOf = p => {
  if (p.value === null || p.value === undefined) return 0
  const v = Number(p.value)
  if (p.alarmMax != null && v > Number(p.alarmMax)) return 2
  if (p.warnMax != null && v > Number(p.warnMax)) return 1
  if (p.alarmMin != null && v < Number(p.alarmMin)) return 2
  if (p.warnMin != null && v < Number(p.warnMin)) return 1
  return 0
}
const statusText = p => ['正常', '预警', '警报'][statusOf(p)]
const statusTag = p => ['success', 'warning', 'danger'][statusOf(p)]
const statusColor = p => ['#67C23A', '#E6A23C', '#F56C6C'][statusOf(p)]
const statusClass = p => (statusOf(p) === 2 ? 'is-alarm' : statusOf(p) === 1 ? 'is-warn' : '')

/** 位置分组展示：固定顺序 一期项目/1号楼/2号楼/东侧/西侧/南侧，其余位置按出现顺序追加 */
const locationOrder = ['一期项目', '1号楼', '2号楼', '东侧', '西侧', '南侧']
const groupedPoints = computed(() => {
  const map = {}
  points.value.forEach(p => {
    const loc = p.locationName || '未分区'
    ;(map[loc] = map[loc] || []).push(p)
  })
  const result = []
  locationOrder.forEach(loc => {
    if (map[loc]) result.push({ name: loc, items: map[loc] })
  })
  Object.keys(map).forEach(loc => {
    if (!locationOrder.includes(loc)) result.push({ name: loc, items: map[loc] })
  })
  return result
})

const selectPoint = p => {
  selected.value = p
  loadDailyStats()
}
const isSelected = p => selected.value && selected.value.pointId === p.pointId

/* ==================== T-23 日统计 ==================== */
const dailyStats = ref([])
const loadDailyStats = async () => {
  if (!selected.value) return
  dailyStats.value = await getEnvDailyStats(selected.value.pointId, 7)
}
const todayStat = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return dailyStats.value.find(d => d.statDate === today) || null
})

/* ==================== T-22 监测点管理 ==================== */
const manageVisible = ref(false)
const devices = ref([])
const subTypes = [
  { name: 'PM2.5', unit: 'μg/m³' },
  { name: 'PM10', unit: 'μg/m³' },
  { name: '噪声', unit: 'dB' },
  { name: '温度', unit: '℃' },
  { name: '湿度', unit: '%' },
  { name: '风速', unit: 'm/s' }
]
const form = ref({})
const editingId = ref(null)

const openManage = async () => {
  manageVisible.value = true
  resetForm()
  await loadDevices()
}
const loadDevices = async () => {
  const data = await getDevicePage({ pageNum: 1, pageSize: 200 })
  devices.value = data.records || []
}
const onSubTypeChange = () => {
  const st = subTypes.find(s => s.name === form.value.monitorSubType)
  if (st) form.value.unit = st.unit
}
const resetForm = () => {
  form.value = { status: 1 }
  editingId.value = null
}
const openEdit = row => {
  editingId.value = row.id
  form.value = { ...row }
}
const submitPoint = async () => {
  if (!form.value.pointName || !form.value.pointCode || !form.value.deviceId || !form.value.monitorSubType) {
    ElMessage.warning('请填写名称、编码、所属设备与监测子类型')
    return
  }
  if (editingId.value) {
    await updateEnvPoint(editingId.value, form.value)
    ElMessage.success('修改成功')
  } else {
    await addEnvPoint(form.value)
    ElMessage.success('新增成功')
  }
  resetForm()
  await loadPoints()
}
const removePoint = async row => {
  await ElMessageBox.confirm(`确定删除监测点「${row.pointName}」吗？`, '提示', { type: 'warning' })
  await deleteEnvPoint(row.id)
  ElMessage.success('已删除')
  await loadPoints()
}
const loadPoints = async () => {
  points.value = await getEnvPoints()
  if (selected.value) {
    const cur = points.value.find(p => p.pointId === selected.value.pointId)
    if (cur) selected.value = cur
  }
}

let unsub = null
onMounted(async () => {
  points.value = await getEnvPoints()
  if (points.value.length) {
    selected.value = points.value[0]
    loadDailyStats()
  }
  unsub = wsClient.subscribe(data => {
    if (data.env) {
      points.value = data.env
    }
  })
})
onUnmounted(() => {
  unsub && unsub()
})
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.mb24 { margin-bottom: 24px; }
.mt16 { margin-top: 16px; }
.mt12 { margin-top: 12px; }
.toolbar { display: flex; justify-content: flex-end; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.loc-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 12px; }
.loc-title .el-tag { font-weight: 400; }
.env-card { cursor: pointer; border-top: 3px solid #67C23A; transition: all .2s; }
.env-card.is-warn { border-top-color: #E6A23C; }
.env-card.is-alarm { border-top-color: #F56C6C; }
.env-card.is-selected { border: 1px solid #409EFF; box-shadow: 0 2px 12px rgba(64, 158, 255, .25); }
.env-head { display: flex; justify-content: space-between; align-items: center; }
.env-name { font-size: 15px; font-weight: 600; }
.env-value { font-size: 30px; font-weight: 700; margin: 10px 0 4px; }
.env-value .unit { font-size: 14px; font-weight: 400; color: #909399; }
.env-sub { font-size: 12px; color: #909399; }
.stat-card { text-align: center; padding: 16px 0; border: 2px solid #e4e7ed; border-radius: 8px; background: #fff; }
.stat-value { font-size: 26px; font-weight: 700; }
.stat-label { margin-top: 4px; color: #606266; font-size: 13px; }
.empty-tip { padding: 16px 0; text-align: center; color: #909399; font-size: 13px; }
</style>
