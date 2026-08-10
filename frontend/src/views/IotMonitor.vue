<template>
  <div class="iot-page">
    <!-- 数据链路说明：让不懂的人也能看懂这个功能是干什么的 -->
    <el-card shadow="never" class="card-block">
      <template #header><span class="card-title">📡 数据链路（这个平台是干什么的）</span></template>
      <div class="chain">
        <div v-for="(s, i) in chainSteps" :key="i" class="chain-step" :class="{ active: i < chainActive }">
          <div class="step-icon">{{ s.icon }}</div>
          <div class="step-name">{{ s.name }}</div>
          <div class="step-desc">{{ s.desc }}</div>
        </div>
        <div v-for="(s, i) in chainSteps" :key="'arrow' + i" class="chain-arrow" v-if="i < chainSteps.length - 1">→</div>
      </div>
      <el-alert :type="platformRunning ? 'success' : 'warning'" :closable="false" class="mt12">
        <template #title>
          <b>{{ platformRunning ? '✅ 模拟设备平台运行中' : '⚠️ 模拟设备平台未启动' }}</b>
          <span v-if="!platformRunning" class="guide">
            &nbsp;启动方法：在 <code>simulator</code> 目录执行 <code>node app.js</code>（或 IDEA 中为该目录配置 Node.js 运行）后，
            5 台虚拟设备将通过 TCP 长连接(:9001)自动上报数据，本页与塔吊/升降机/环境监控页即出现实时数据。
          </span>
        </template>
      </el-alert>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-num">{{ overview.activeConnections ?? '-' }}</div>
          <div class="stat-label">TCP 在线设备</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-num">{{ overview.totalReports ?? '-' }}</div>
          <div class="stat-label">累计上报报文</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-num">{{ overview.totalConnections ?? '-' }}</div>
          <div class="stat-label">累计连接数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-num">{{ fmt(overview.latestReportTime) }}</div>
          <div class="stat-label">最近上报时间</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 手动推送（演示人调节推送数据：单次推送 / 周期推送 / 停止推送） -->
    <el-card shadow="never" class="card-block">
      <template #header>
        <span class="card-title">✍️ 手动推送（演示人可自定义报文内容）</span>
        <div class="filter-bar">
          <el-tag v-for="code in simRunning" :key="code" type="warning" size="small" class="sim-run-tag">
            ⏱ {{ code }} 周期推送中
          </el-tag>
        </div>
      </template>
      <el-form :inline="false" label-width="90px" class="sim-form">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="模拟设备">
              <el-select v-model="simForm.deviceCode" placeholder="选择设备" style="width: 100%" @change="applyTemplate">
                <el-option v-for="d in simDevices" :key="d.code" :label="d.name" :value="d.code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="周期(秒)">
              <el-input-number v-model="simForm.intervalSec" :min="1" :max="3600" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设备类型">
              <el-input :model-value="simTypeName" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="报文内容">
          <el-input v-model="simForm.payload" type="textarea" :rows="6" class="payload-input"
            placeholder='{"load":3.5,"radius":25.0,"wind_speed":6.5,"height":45.0,"angle":120.5}' />
        </el-form-item>
        <el-form-item label="快捷模板">
          <el-tag v-for="t in simTemplates" :key="t.label" class="sim-tpl" effect="plain" @click="applyTemplate(t.code)">
            {{ t.label }}
          </el-tag>
        </el-form-item>
        <el-form-item label="操作">
          <el-button type="primary" :loading="simLoading" @click="doSimPush">
            <el-icon><Promotion /></el-icon>&nbsp;单次推送
          </el-button>
          <el-button type="success" :loading="simLoading" @click="doSimStart">
            <el-icon><VideoPlay /></el-icon>&nbsp;周期推送
          </el-button>
          <el-button type="danger" :loading="simLoading" @click="doSimStop">
            <el-icon><VideoPause /></el-icon>&nbsp;停止推送
          </el-button>
          <span class="sim-hint">单次推送立即上报一条报文；周期推送按设定秒数持续上报，可随时停止</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 设备连接状态（卡片式，更直观） -->
    <el-card shadow="never" class="card-block">
      <template #header><span class="card-title">模拟设备连接状态</span></template>
      <el-empty v-if="!devices.length" description="暂无设备连接（请先启动 simulator 目录的 node app.js）" :image-size="80" />
      <el-row :gutter="16" v-else>
        <el-col :span="8" v-for="d in devices" :key="d.deviceCode" class="device-col">
          <div class="device-card" :class="{ offline: !d.lastReportTime }">
            <div class="dev-head">
              <span class="dev-name">{{ deviceName(d.deviceCode) }}</span>
              <span class="status-dot" :class="d.lastReportTime ? 'on' : 'off'"></span>
            </div>
            <div class="dev-code">{{ d.deviceCode }} · {{ typeName(d.deviceCode) }}</div>
            <div class="dev-meta">
              <div>累计上报：<b>{{ d.reportCount }}</b> 条</div>
              <div>最近上报：{{ fmt(d.lastReportTime) || '-' }}</div>
              <div>连接时间：{{ fmt(d.connectTime) || '-' }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 上报记录 -->
    <el-card shadow="never" class="card-block">
      <template #header>
        <span class="card-title">上报记录（落库 t_iot_data）</span>
        <div class="filter-bar">
          <el-select v-model="query.subType" placeholder="数据类型" clearable size="small" style="width: 140px">
            <el-option label="塔吊 (crane)" value="crane" />
            <el-option label="升降机 (lift)" value="lift" />
            <el-option label="环境 (env)" value="env" />
          </el-select>
          <el-button size="small" type="primary" @click="loadRecords(1)">查询</el-button>
        </div>
      </template>
      <el-table :data="records" stripe size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="设备标识" width="100">
          <template #default="{ row }">{{ tagName(row.deviceTag) }}</template>
        </el-table-column>
        <el-table-column label="数据类型" width="110">
          <template #default="{ row }">{{ row.dataSubType }}</template>
        </el-table-column>
        <el-table-column prop="reportTime" label="上报时间" width="180" />
        <el-table-column label="报文内容">
          <template #default="{ row }">
            <pre class="payload">{{ row.payload }}</pre>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.pageNum"
        @current-change="loadRecords"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getIotOverview, getIotConnections, getIotRecords, iotSimPush, iotSimStart, iotSimStop, getIotSimStatus } from '../api/coze'

const overview = ref({})
const devices = ref([])
const records = ref([])
const total = ref(0)
const query = ref({ pageNum: 1, pageSize: 10, subType: '' })
let timer = null

/* ==================== 手动推送（演示人调节推送数据） ==================== */
const simDevices = [
  { code: 'TC-001', name: '1#塔吊', type: 'crane', typeName: '塔吊' },
  { code: 'TC-002', name: '2#塔吊', type: 'crane', typeName: '塔吊' },
  { code: 'LFT-001', name: '1#施工升降机', type: 'lift', typeName: '升降机' },
  { code: 'ENV-001', name: '环境监测站1', type: 'env', typeName: '环境监测' },
  { code: 'ENV-002', name: '环境监测站2', type: 'env', typeName: '环境监测' }
]
const simTemplates = [
  { label: '塔吊', code: 'TC-001' },
  { label: '升降机', code: 'LFT-001' },
  { label: '环境', code: 'ENV-001' }
]
// 各类设备报文模板（演示人可直接修改数值后推送，制造异常/告警演示）
const PAYLOAD_TEMPLATES = {
  'TC-001': { 'load': 3.5, 'radius': 25.0, 'wind_speed': 6.5, 'height': 45.0, 'angle': 120.5 },
  'TC-002': { 'load': 4.2, 'radius': 22.0, 'wind_speed': 5.8, 'height': 38.0, 'angle': 200.0 },
  'LFT-001': { 'load_weight': 1200, 'person_count': 6, 'height': 42.5, 'wind_speed': 5.0, 'direction': 1, 'door_front': 1, 'door_back': 0 },
  'ENV-001': { 'PM2.5': 62.5, 'PM10': 118.0, '噪声': 66.2, '温度': 28.6, '湿度': 61.0, '风速': 5.2 },
  'ENV-002': { 'PM2.5': 55.0, 'PM10': 102.0, '噪声': 63.5, '温度': 29.1, '湿度': 58.5, '风速': 4.8 }
}
const simForm = ref({ deviceCode: 'TC-001', intervalSec: 5, payload: '' })
const simRunning = ref([])
const simLoading = ref(false)

const simTypeName = computed(() => {
  const d = simDevices.find(x => x.code === simForm.value.deviceCode)
  return d ? d.typeName : '-'
})

/** 选择设备/快捷模板后回填对应报文模板（可直接修改数值） */
const applyTemplate = code => {
  const device = simDevices.find(d => d.code === code)
  if (!device) return
  simForm.value.deviceCode = device.code
  const data = PAYLOAD_TEMPLATES[device.code] || {}
  simForm.value.payload = JSON.stringify(data, null, 2)
}

/** 解析报文内容为对象 */
const parsePayload = () => {
  try {
    const obj = JSON.parse(simForm.value.payload)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) throw new Error('bad')
    return obj
  } catch (e) {
    ElMessage.error('报文内容不是合法的 JSON 对象，请检查格式')
    return null
  }
}

/** 单次推送 */
const doSimPush = async () => {
  const data = parsePayload()
  if (!data || !simForm.value.deviceCode) return
  const device = simDevices.find(d => d.code === simForm.value.deviceCode)
  simLoading.value = true
  try {
    await iotSimPush({ deviceCode: simForm.value.deviceCode, type: device?.type || 'other', data })
    ElMessage.success('已单次推送一条报文')
    loadRecords(1)
    loadOverview()
  } finally {
    simLoading.value = false
  }
}

/** 周期推送 */
const doSimStart = async () => {
  const data = parsePayload()
  if (!data || !simForm.value.deviceCode) return
  const device = simDevices.find(d => d.code === simForm.value.deviceCode)
  simLoading.value = true
  try {
    await iotSimStart({
      deviceCode: simForm.value.deviceCode,
      type: device?.type || 'other',
      data,
      intervalSec: simForm.value.intervalSec || 5
    })
    ElMessage.success(`已开始周期推送（每 ${simForm.value.intervalSec || 5} 秒一次）`)
    loadSimStatus()
  } finally {
    simLoading.value = false
  }
}

/** 停止推送 */
const doSimStop = async () => {
  if (!simForm.value.deviceCode) return
  simLoading.value = true
  try {
    await iotSimStop({ deviceCode: simForm.value.deviceCode })
    ElMessage.success('已停止周期推送')
    loadSimStatus()
  } finally {
    simLoading.value = false
  }
}

/** 周期推送状态 */
const loadSimStatus = async () => {
  try {
    const res = await getIotSimStatus()
    simRunning.value = res?.running || []
  } catch (e) { /* 忽略 */ }
}

/* 数据链路步骤（可视化说明） */
const chainSteps = [
  { icon: '🏗️', name: '① 模拟设备', desc: 'simulator 目录的 Node 程序模拟 5 台工地设备（塔吊/升降机/环境站）' },
  { icon: '🔌', name: '② TCP 长连接', desc: '设备每 5 秒通过 TCP(:9001) 上报 JSON 报文' },
  { icon: '🖥️', name: '③ 后端接收', desc: 'SpringBoot 接收报文，原文存档 t_iot_data 并解析指标' },
  { icon: '📊', name: '④ 实时数据', desc: '写入 t_realtime_data，驱动塔吊/升降机/环境监控页' },
  { icon: '🔔', name: '⑤ 告警判断', desc: '超阈值自动生成告警并 WebSocket 推送' },
]
const chainActive = computed(() => (platformRunning.value ? chainSteps.length : 0))
const platformRunning = computed(() => (overview.value.activeConnections || 0) > 0)

const typeName = code => {
  if (code.startsWith('TC')) return '塔吊'
  if (code.startsWith('LFT')) return '升降机'
  if (code.startsWith('ENV')) return '环境监测'
  return '其他'
}
const deviceName = code => ({
  'TC-001': '1#塔吊', 'TC-002': '2#塔吊', 'LFT-001': '1#施工升降机',
  'ENV-001': '环境监测站1', 'ENV-002': '环境监测站2'
}[code] || code)
const tagName = tag => ({ its: '塔吊(its)', shs: '升降机(shs)', ic: '环境(ic)', ax: '其他(ax)' }[tag] || tag)
const fmt = t => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

const loadOverview = async () => {
  try {
    // request 拦截器已解包 Result.data，直接使用返回对象
    const [ov, conn] = await Promise.all([getIotOverview(), getIotConnections()])
    overview.value = ov || {}
    devices.value = conn?.connectedDevices || []
  } catch (e) { /* 后端未启动时忽略 */ }
}

const loadRecords = async (page) => {
  if (page) query.value.pageNum = page
  try {
    const res = await getIotRecords(query.value)
    records.value = res.records || []
    total.value = Number(res.total || 0)
  } catch (e) { /* 忽略 */ }
}

onMounted(() => {
  applyTemplate('TC-001')
  loadOverview()
  loadRecords()
  loadSimStatus()
  timer = setInterval(() => {
    loadOverview()
    loadSimStatus()
  }, 3000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.card-block { margin-bottom: 16px; }
.card-title { font-weight: 600; }
.mt12 { margin-top: 12px; }
.guide { font-size: 12px; }
.guide code { background: #f0f2f5; padding: 1px 6px; border-radius: 4px; }
.chain { display: flex; align-items: stretch; gap: 8px; flex-wrap: wrap; }
.chain-step {
  flex: 1; min-width: 150px; background: #fafbfc; border: 1px solid #ebeef5;
  border-radius: 8px; padding: 12px; text-align: center; opacity: .55;
}
.chain-step.active { opacity: 1; border-color: #409EFF; background: #f0f7ff; }
.step-icon { font-size: 24px; }
.step-name { font-weight: 600; font-size: 13px; margin: 6px 0 4px; color: #303133; }
.step-desc { font-size: 12px; color: #909399; line-height: 1.6; }
.chain-arrow { align-self: center; color: #c0c4cc; font-size: 16px; }
.stat-row { margin-bottom: 16px; }
.stat-num { font-size: 24px; font-weight: 700; color: #409EFF; text-align: center; }
.stat-label { text-align: center; color: #909399; font-size: 13px; margin-top: 6px; }
.device-col { margin-bottom: 16px; }
.device-card {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; background: #fff;
  border-left: 4px solid #67c23a;
}
.device-card.offline { border-left-color: #f56c6c; opacity: .7; }
.dev-head { display: flex; justify-content: space-between; align-items: center; }
.dev-name { font-weight: 600; font-size: 14px; color: #303133; }
.status-dot { width: 10px; height: 10px; border-radius: 50%; }
.status-dot.on { background: #67c23a; box-shadow: 0 0 6px #67c23a; }
.status-dot.off { background: #f56c6c; }
.dev-code { font-size: 12px; color: #909399; margin: 4px 0 8px; }
.dev-meta { font-size: 12px; color: #606266; line-height: 1.9; }
.filter-bar { float: right; display: flex; gap: 8px; align-items: center; }
.sim-run-tag { margin-left: 4px; }
.sim-form { max-width: 900px; }
.sim-form .el-form-item { margin-bottom: 14px; }
.payload-input :deep(textarea) { font-family: Consolas, "Courier New", monospace; font-size: 12px; }
.sim-tpl { cursor: pointer; margin-right: 8px; }
.sim-tpl:hover { color: #409EFF; border-color: #409EFF; }
.sim-hint { margin-left: 12px; font-size: 12px; color: #909399; }
.payload {
  background: #f8f8f8; border-radius: 4px; padding: 6px 8px; font-size: 12px;
  white-space: pre-wrap; word-break: break-all; margin: 0;
}
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
