<template>
  <div v-loading="loading">
    <el-card shadow="never" class="mb16">
      <template #header>
        <div class="card-header">
          <span><el-icon><Monitor /></el-icon> {{ detail.device?.deviceName || '设备详情' }}
            <el-tag v-if="detail.device" size="small" :type="detail.device.status === 1 ? 'success' : 'danger'" class="ml8">
              {{ detail.device.status === 1 ? '在线' : '离线' }}
            </el-tag>
            <el-tag v-if="detail.device" size="small" :type="detail.device.enableStatus === 1 ? 'primary' : 'info'" class="ml8">
              {{ detail.device.enableStatus === 1 ? '启用' : '禁用' }}
            </el-tag>
          </span>
          <div>
            <el-button @click="router.back()">返回列表</el-button>
            <el-button type="success" v-permission="'sys:device:edit'" @click="goEdit">编辑设备</el-button>
          </div>
        </div>
      </template>
      <el-descriptions v-if="detail.device" :column="4" border>
        <el-descriptions-item label="设备编码">{{ detail.device.deviceCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.device.typeName }}</el-descriptions-item>
        <el-descriptions-item label="安装位置">{{ detail.device.locationName }}</el-descriptions-item>
        <el-descriptions-item label="品牌/型号">{{ detail.device.brand }} {{ detail.device.model }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.device.supplier }}</el-descriptions-item>
        <el-descriptions-item label="设备原值">{{ detail.device.originalValue }} 元</el-descriptions-item>
        <el-descriptions-item label="平面坐标">{{ detail.device.coordinate }}</el-descriptions-item>
        <el-descriptions-item label="二维码编号">{{ detail.device.qrCode || '未生成' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="4">{{ detail.device.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 全生命周期（T-12） -->
    <el-card shadow="never" class="mb16">
      <template #header><span><el-icon><Calendar /></el-icon> 全生命周期</span></template>
      <el-steps v-if="detail.device" :active="lifecycleActive" align-center finish-status="success" class="lifecycle-steps">
        <el-step title="生产" :description="detail.device.produceDate || '未填写'" />
        <el-step title="供货" :description="detail.device.supplyDate || '未填写'" />
        <el-step title="验收" :description="detail.device.acceptDate || '未填写'" />
        <el-step title="安装" :description="detail.device.installDate || '未填写'" />
        <el-step title="启用" :description="detail.device.enableDate || '未填写'" />
        <el-step title="预计报废" :description="detail.device.expectScrapDate || '未填写'" />
        <el-step title="实际报废" :description="detail.device.actualScrapDate || '未报废'" />
      </el-steps>
      <div v-if="detail.device" class="lifecycle-extra">
        设计使用年限：{{ detail.device.designServiceLife || '-' }} 年　|　最近维修：{{ detail.device.lastMaintainDate || '-' }}
      </div>
    </el-card>

    <el-row :gutter="16" class="mb16">
      <!-- 实时数据（T-09） -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span><el-icon><Odometer /></el-icon> 实时数据</span></template>
          <el-empty v-if="!realtime.length" description="暂无实时数据" :image-size="60" />
          <div v-else class="realtime-grid">
            <div v-for="r in realtime" :key="r.id" class="rt-item" :class="rtClass(r)">
              <div class="rt-value">{{ r.paramValue }} <span class="rt-unit">{{ r.unit }}</span></div>
              <div class="rt-label">{{ r.paramCode }}</div>
              <div class="rt-time">{{ r.collectTime }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <!-- 在线率（T-11） -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span><el-icon><Timer /></el-icon> 在线率统计（近 {{ rateDays }} 天）</span></template>
          <div class="rate-panel">
            <div class="rate-value">{{ onlineRate.onlineRate ?? '-' }}<span class="rate-unit">%</span></div>
            <div class="rate-meta">
              <div>离线次数：<b>{{ onlineRate.offlineCount ?? '-' }}</b></div>
              <div>状态变更次数：<b>{{ onlineRate.changeCount ?? '-' }}</b></div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 监测点管理（T-10） -->
    <el-card shadow="never" class="mb16">
      <template #header>
        <div class="card-header">
          <span><el-icon><SetUp /></el-icon> 监测点配置（{{ points.length }}）</span>
          <el-button type="success" size="small" v-permission="'sys:device:point'" @click="openPointEdit(null)">新增监测点</el-button>
        </div>
      </template>
      <el-table :data="points" border stripe size="small">
        <el-table-column prop="pointCode" label="编码" width="130" />
        <el-table-column prop="pointName" label="名称" min-width="110" />
        <el-table-column prop="monitorSubType" label="监测类型" width="90" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column label="预警范围" width="130" align="center">
          <template #default="{ row }">{{ fmtRange(row.warnMin, row.warnMax) }}</template>
        </el-table-column>
        <el-table-column label="报警范围" width="130" align="center">
          <template #default="{ row }">{{ fmtRange(row.alarmMin, row.alarmMax) }}</template>
        </el-table-column>
        <el-table-column label="喷淋联动" width="200">
          <template #default="{ row }">
            <template v-if="row.sprayEnabled === 1">
              <el-tag size="small" type="warning">联动</el-tag>
              <span class="spray-info">开≥{{ row.sprayOnThreshold }} 关&lt;{{ row.sprayOffThreshold }}</span>
            </template>
            <span v-else class="muted">未启用</span>
          </template>
        </el-table-column>
        <el-table-column prop="collectInterval" label="采集间隔(s)" width="95" align="center" />
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" v-permission="'sys:device:point'" @click="openPointEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" v-permission="'sys:device:point'" @click="removePoint(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 历史数据（T-09） -->
    <el-card shadow="never" class="mb16">
      <template #header>
        <div class="card-header">
          <span><el-icon><TrendCharts /></el-icon> 历史数据曲线</span>
          <div>
            <el-select v-model="historyPoint" placeholder="选择监测点/参数" size="small" style="width: 180px">
              <el-option v-for="p in historyOptions" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
            <el-select v-model="historyHours" size="small" style="width: 110px; margin-left: 8px" @change="loadHistory">
              <el-option label="近1小时" :value="1" />
              <el-option label="近6小时" :value="6" />
              <el-option label="近24小时" :value="24" />
              <el-option label="近72小时" :value="72" />
            </el-select>
            <el-button size="small" type="primary" class="ml8" @click="loadHistory">查询</el-button>
          </div>
        </div>
      </template>
      <div ref="historyRef" class="history-chart"></div>
    </el-card>

    <!-- 告警记录（T-09） -->
    <el-card shadow="never" class="mb16">
      <template #header><span><el-icon><Bell /></el-icon> 告警记录（近30天，{{ detail.alarms?.length || 0 }} 条）</span></template>
      <el-table :data="detail.alarms || []" border stripe size="small">
        <el-table-column prop="alarmNo" label="告警编号" width="160" />
        <el-table-column prop="alarmContent" label="告警内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="级别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="['info', 'warning', 'danger'][row.alarmLevel - 1] || 'info'" size="small">
              {{ ['预警', '警报', '控制'][row.alarmLevel - 1] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alarmValue" label="触发值" width="90" align="right" />
        <el-table-column prop="alarmTime" label="报警时间" width="165" />
        <el-table-column label="处置状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="['danger', 'warning', 'success'][row.handleStatus] || 'info'" size="small">
              {{ ['未处置', '处置中', '已处置'][row.handleStatus] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 离线记录（T-11） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><SwitchButton /></el-icon> 离线/上线记录（近30天，{{ detail.offlineRecords?.length || 0 }} 条）</span>
        </div>
      </template>
      <el-table :data="detail.offlineRecords || []" border stripe size="small">
        <el-table-column prop="recordTime" label="变更时间" width="180" />
        <el-table-column label="变更类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 2 ? 'success' : 'danger'" size="small">
              {{ row.status === 2 ? '上线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 监测点编辑弹窗 -->
    <el-dialog v-model="pointVisible" :title="pointForm.id ? '编辑监测点' : '新增监测点'" width="640px" destroy-on-close>
      <el-form :model="pointForm" label-width="110px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="监测点编码" required>
              <el-input v-model="pointForm.pointCode" :disabled="!!pointForm.id" placeholder="如 TC-MOMENT-01" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="监测点名称" required><el-input v-model="pointForm.pointName" /></el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="监测子类型">
              <el-select v-model="pointForm.monitorSubType" allow-create filterable style="width: 100%">
                <el-option v-for="t in subTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位"><el-input v-model="pointForm.unit" placeholder="如 t、m/s、kg" /></el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="预警下限"><el-input-number v-model="pointForm.warnMin" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预警上限"><el-input-number v-model="pointForm.warnMax" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="报警下限"><el-input-number v-model="pointForm.alarmMin" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报警上限"><el-input-number v-model="pointForm.alarmMax" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="采集间隔(秒)"><el-input-number v-model="pointForm.collectInterval" :min="1" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="pointForm.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">喷淋联动</el-divider>
        <el-form-item label="启用喷淋联动">
          <el-switch v-model="pointForm.sprayEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <template v-if="pointForm.sprayEnabled === 1">
          <el-row>
            <el-col :span="12">
              <el-form-item label="启动阈值"><el-input-number v-model="pointForm.sprayOnThreshold" :precision="2" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="关闭阈值"><el-input-number v-model="pointForm.sprayOffThreshold" :precision="2" style="width: 100%" /></el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="关联喷淋设备">
            <el-select v-model="pointForm.sprayDeviceId" clearable filterable style="width: 100%" placeholder="选择喷淋设备">
              <el-option v-for="s in sprayDevices" :key="s.id" :label="s.deviceName + ' (' + s.deviceCode + ')'" :value="s.id" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="pointVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPoint">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  getDeviceDetailAgg, getDeviceRealtime, getDeviceHistory, getOfflineRecords, getOnlineRate,
  addDevicePoint, updateDevicePoint, deleteDevicePoint, getDevicePage
} from '../api/device'

const route = useRoute()
const router = useRouter()
const deviceId = Number(route.params.id)

const loading = ref(false)
const detail = ref({})
const realtime = ref([])
const points = ref([])
const submitting = ref(false)

const rateDays = 30
const onlineRate = ref({})

const subTypes = ['力矩', '吊重', '风速', '载重', '载人', 'PM2.5', 'PM10', '噪声', '温度', '湿度', '压力', '位移']

// 生命周期步骤 active：按已填写的阶段数
const lifecycleActive = computed(() => {
  const d = detail.value.device
  if (!d) return 0
  return [d.produceDate, d.supplyDate, d.acceptDate, d.installDate, d.enableDate, d.expectScrapDate, d.actualScrapDate]
    .filter(Boolean).length - (d.actualScrapDate ? 0 : 1) || 0
})

const loadAll = async () => {
  loading.value = true
  try {
    detail.value = await getDeviceDetailAgg(deviceId)
    realtime.value = await getDeviceRealtime(deviceId)
    points.value = detail.value.points || []
    onlineRate.value = await getOnlineRate(deviceId, { days: rateDays })
    loadHistory()
  } finally {
    loading.value = false
  }
}

// 实时数据超限判定（对比监测点预警/报警上下限）
const rtClass = r => {
  const p = points.value.find(x => x.pointId === r.pointId || x.pointCode === r.paramCode)
  if (!p) return ''
  const v = Number(r.paramValue)
  if (p.alarmMin !== null && p.alarmMin !== undefined && v < p.alarmMin) return 'is-alarm'
  if (p.alarmMax !== null && p.alarmMax !== undefined && v > p.alarmMax) return 'is-alarm'
  if (p.warnMin !== null && p.warnMin !== undefined && v < p.warnMin) return 'is-warn'
  if (p.warnMax !== null && p.warnMax !== undefined && v > p.warnMax) return 'is-warn'
  return 'is-normal'
}

const fmtRange = (min, max) => {
  if (min === null && max === null) return '-'
  return `${min ?? '-∞'} ~ ${max ?? '+∞'}`
}

// ============ 监测点管理 ============
const pointVisible = ref(false)
const pointForm = ref({})
const sprayDevices = ref([])

const openPointEdit = row => {
  pointForm.value = row
    ? { ...row }
    : { id: null, pointCode: '', deviceId, pointName: '', monitorType: 'device', monitorSubType: '',
        unit: '', warnMin: null, warnMax: null, alarmMin: null, alarmMax: null,
        sprayEnabled: 0, sprayOnThreshold: null, sprayOffThreshold: null, sprayDeviceId: null,
        collectInterval: 30, status: 1 }
  pointVisible.value = true
}

const submitPoint = async () => {
  if (!pointForm.value.pointCode || !pointForm.value.pointName) {
    ElMessage.warning('请填写监测点编码和名称')
    return
  }
  submitting.value = true
  try {
    if (pointForm.value.id) {
      await updateDevicePoint(pointForm.value.id, pointForm.value)
    } else {
      await addDevicePoint(pointForm.value)
    }
    ElMessage.success('保存成功')
    pointVisible.value = false
    const agg = await getDeviceDetailAgg(deviceId)
    points.value = agg.points || []
  } finally {
    submitting.value = false
  }
}

const removePoint = async row => {
  await ElMessageBox.confirm(`确定删除监测点「${row.pointName}」吗？`, '提示', { type: 'warning' })
  await deleteDevicePoint(row.id)
  ElMessage.success('已删除')
  const agg = await getDeviceDetailAgg(deviceId)
  points.value = agg.points || []
}

// ============ 历史数据曲线 ============
const historyRef = ref()
const historyPoint = ref('')
const historyHours = ref(24)
let historyChart = null

// 实时参数 → 中文名
const PARAM_LABELS = {
  load: '吊重', radius: '幅度', wind_speed: '风速', height: '高度', angle: '角度',
  load_weight: '载重', person_count: '载人', door_front: '前门', door_back: '后门',
  direction: '运行方向'
}

// 历史曲线选项：基于实时参数编码（模拟器按 param_code 写库，point_id 恒为空；
// 按参数查历史才能保证有数据），监测点配置仅用于超限判定与告警。
const historyOptions = computed(() => {
  const seen = {}
  const opts = []
  ;(realtime.value || []).forEach(r => {
    if (seen[r.paramCode]) return
    seen[r.paramCode] = true
    opts.push({
      value: r.paramCode,
      label: (PARAM_LABELS[r.paramCode] || r.paramCode) + (r.unit ? ' (' + r.unit + ')' : '')
    })
  })
  return opts
})

const loadHistory = async () => {
  const point = historyPoint.value || historyOptions.value[0]?.value
  if (point === undefined || point === null || point === '') return
  // 数字 → pointId；字符串 → paramCode
  const params = { hours: historyHours.value }
  if (typeof point === 'number') {
    params.pointId = point
  } else {
    params.paramCode = point
  }
  const list = await getDeviceHistory(deviceId, params)
  renderHistory(list)
}

watch(historyPoint, () => loadHistory())

const renderHistory = list => {
  if (!historyRef.value) return
  if (!historyChart) historyChart = echarts.init(historyRef.value)
  historyChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: list.map(d => d.collectTime), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' },
    series: [{
      type: 'line', smooth: true, data: list.map(d => Number(d.paramValue)),
      itemStyle: { color: '#409EFF' }, areaStyle: { opacity: 0.12 }
    }]
  })
}

const resize = () => historyChart && historyChart.resize()

const goEdit = () => {
  ElMessage.info('请在设备台账页编辑设备信息（返回列表 → 编辑）')
  router.push('/device')
}

onMounted(async () => {
  await loadAll()
  // 喷淋设备下拉
  const devs = await getDevicePage({ pageNum: 1, pageSize: 100 })
  sprayDevices.value = (devs.records || []).filter(d => (d.typeName || '').includes('喷淋'))
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  historyChart && historyChart.dispose()
})
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.ml8 { margin-left: 8px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.lifecycle-steps { margin: 8px 0 16px; }
.lifecycle-extra { color: #909399; font-size: 13px; text-align: center; }
.realtime-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.rt-item { border-radius: 6px; padding: 12px; text-align: center; border: 1px solid #e4e7ed; }
.rt-item.is-normal { background: #f0f9eb; }
.rt-item.is-warn { background: #fdf6ec; border-color: #e6a23c; }
.rt-item.is-alarm { background: #fef0f0; border-color: #f56c6c; }
.rt-value { font-size: 22px; font-weight: 700; color: #303133; }
.rt-unit { font-size: 12px; color: #909399; }
.rt-label { margin-top: 2px; color: #606266; font-size: 13px; }
.rt-time { color: #c0c4cc; font-size: 11px; margin-top: 2px; }
.rate-panel { text-align: center; padding: 10px 0; }
.rate-value { font-size: 40px; font-weight: 700; color: #409EFF; }
.rate-unit { font-size: 16px; color: #909399; margin-left: 2px; }
.rate-meta { color: #606266; margin-top: 10px; line-height: 1.8; }
.history-chart { height: 300px; }
.spray-info { margin-left: 6px; font-size: 12px; color: #e6a23c; }
.muted { color: #c0c4cc; }
</style>
