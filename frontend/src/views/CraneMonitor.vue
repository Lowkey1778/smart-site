<template>
  <div>
    <el-alert type="info" :closable="false" class="mb16"
      title="数据由设备模拟器每 5 秒生成并通过 WebSocket 实时推送，力矩 = 吊重 × 幅度（实时计算）" />
    <div class="mb16 toolbar">
      <el-button type="primary" @click="openRecords"><el-icon><Document /></el-icon>&nbsp;作业记录</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :span="8" v-for="crane in cranes" :key="crane.deviceId">
        <el-card shadow="hover" :class="['crane-card', crane.status !== 1 ? 'is-offline' : '', riskClass(crane)]">
          <div class="card-head">
            <div>
              <div class="name">{{ crane.deviceName }}</div>
              <div class="code">{{ crane.deviceCode }}</div>
            </div>
            <el-tag :type="crane.status === 1 ? 'success' : 'danger'">
              {{ crane.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </div>

          <div class="moment-box">
            <div class="moment-label">当前力矩</div>
            <div class="moment-value" :style="{ color: riskColor(crane) }">
              {{ fmt(crane.moment) }} <span class="unit">t·m</span>
            </div>
            <el-progress :percentage="percent(crane.momentPercent)" :color="progressColor" :stroke-width="10" />
            <div class="moment-sub">
              额定 {{ fmt(crane.ratedMoment) }} t·m · 占比 {{ fmt(crane.momentPercent) }}%
            </div>
          </div>

          <el-descriptions :column="3" size="small" class="mt12">
            <el-descriptions-item label="吊重">{{ fmt(crane.loadVal) }} t</el-descriptions-item>
            <el-descriptions-item label="幅度">{{ fmt(crane.radiusVal) }} m</el-descriptions-item>
            <el-descriptions-item label="风速">{{ fmt(crane.windSpeed) }} m/s</el-descriptions-item>
            <el-descriptions-item label="高度">{{ fmt(crane.height) }} m</el-descriptions-item>
            <el-descriptions-item label="角度">{{ fmt(crane.angle) }}°</el-descriptions-item>
            <el-descriptions-item label="额定载荷">{{ fmt(crane.ratedLoad) }} t</el-descriptions-item>
          </el-descriptions>

          <el-button type="primary" size="small" class="mt12" @click="showDetail(crane)">查看详情</el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detail && detail.deviceName" width="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备编码">{{ detail.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="运行状态">
            <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small">
              {{ detail.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="前臂长">{{ fmt(detail.frontArmLen) }} m</el-descriptions-item>
          <el-descriptions-item label="最大高度">{{ fmt(detail.maxHeight) }} m</el-descriptions-item>
          <el-descriptions-item label="额定载荷">{{ fmt(detail.ratedLoad) }} t</el-descriptions-item>
          <el-descriptions-item label="最大载荷">{{ fmt(detail.maxLoad) }} t</el-descriptions-item>
          <el-descriptions-item label="额定力矩">{{ fmt(detail.ratedMoment) }} t·m</el-descriptions-item>
          <el-descriptions-item label="当前力矩">{{ fmt(detail.moment) }} t·m ({{ fmt(detail.momentPercent) }}%)</el-descriptions-item>
          <el-descriptions-item label="吊重">{{ fmt(detail.loadVal) }} t</el-descriptions-item>
          <el-descriptions-item label="幅度">{{ fmt(detail.radiusVal) }} m</el-descriptions-item>
          <el-descriptions-item label="风速">{{ fmt(detail.windSpeed) }} m/s</el-descriptions-item>
          <el-descriptions-item label="吊钩高度">{{ fmt(detail.height) }} m</el-descriptions-item>
          <el-descriptions-item label="回转角度">{{ fmt(detail.angle) }}°</el-descriptions-item>
          <el-descriptions-item label="数据更新">WebSocket 实时推送</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="detail.momentPercent && detail.momentPercent >= 90" type="error" :closable="false"
          class="mt12" title="力矩接近/超过额定值，存在超载风险！" />

        <!-- T-33 安全状态预测：CNN+LSTM+Attention 时序预测 -->
        <div class="predict-box mt12">
          <div class="predict-title">
            <el-icon><DataAnalysis /></el-icon>
            <span>安全状态预测（CNN+LSTM+Attention）</span>
            <el-tag v-if="predict" size="small" :type="predictLevelType" class="ml8">{{ predict.level }}</el-tag>
          </div>
          <div v-if="predictLoading" class="predict-tip">预测计算中…</div>
          <div v-else-if="predictError" class="predict-tip">{{ predictError }}</div>
          <div v-else-if="predict" class="predict-body">
            <el-descriptions :column="3" size="small">
              <el-descriptions-item label="预测吊重">{{ predict.predLoad }} t</el-descriptions-item>
              <el-descriptions-item label="当前吊重">{{ predict.curLoad }} t</el-descriptions-item>
              <el-descriptions-item label="当前风速">{{ predict.curWindSpeed }} m/s</el-descriptions-item>
            </el-descriptions>
            <div class="risk-bar">
              <span class="risk-label">风险概率</span>
              <el-progress :percentage="Math.round(predict.riskProb * 100)"
                :color="riskProbColor" :stroke-width="10" style="flex:1" />
              <span class="risk-val">{{ (predict.riskProb * 100).toFixed(1) }}%</span>
            </div>
            <div class="advice">{{ predict.advice }}</div>
          </div>
          <div v-else class="predict-tip">暂无预测数据</div>
        </div>
      </template>
    </el-dialog>
  </div>

    <!-- 塔吊作业记录弹窗（T-14 / RQ-16） -->
    <el-dialog v-model="recordVisible" title="塔吊作业记录" width="920px">
      <el-form :inline="true" class="mb12">
        <el-form-item label="塔吊">
          <el-select v-model="recordQuery.deviceId" placeholder="全部塔吊" clearable style="width: 160px" @change="loadRecords">
            <el-option v-for="cr in cranes" :key="cr.deviceId" :label="cr.deviceName" :value="cr.deviceId" />
          </el-select>
        </el-form-item>
        <el-button size="small" @click="loadRecords">刷新</el-button>
      </el-form>
      <el-table :data="records" size="small" border stripe max-height="420">
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column prop="hoistingWeight" label="吊重(t)" width="90" align="center" />
        <el-table-column prop="maxLoadPercent" label="载荷%" width="90" align="center" />
        <el-table-column prop="maxWindSpeed" label="风速" width="90" align="center" />
        <el-table-column label="吊点位置" width="130" align="center">
          <template #default="{ row }">{{ fmt(row.hookRadius) }}m / {{ fmt(row.hookHeight) }}m</template>
        </el-table-column>
        <el-table-column label="卸料位置" width="130" align="center">
          <template #default="{ row }">{{ fmt(row.unloadRadius) }}m / {{ fmt(row.unloadHeight) }}m</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-pagination class="mt12" background layout="total, prev, pager, next" :total="recordTotal"
        :page-size="recordQuery.pageSize" :current-page="recordQuery.pageNum" @current-change="onRecordPage" />
    </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getCraneList, getCraneRecords, getCranePredict } from '../api/monitor'
import wsClient from '../api/ws'

const cranes = ref([])
const detailVisible = ref(false)
const detail = ref(null)

const fmt = v => (v === null || v === undefined ? '-' : Number(v).toLocaleString())
const percent = p => Math.min(100, Number(p || 0))

/* ===== T-33 安全状态预测 ===== */
const predict = ref(null)
const predictLoading = ref(false)
const predictError = ref('')

const predictLevelType = computed(() => {
  if (!predict.value) return 'info'
  return { '高风险': 'danger', '中风险': 'warning', '低风险': 'success' }[predict.value.level] || 'info'
})
const riskProbColor = p => {
  if (p >= 70) return '#F56C6C'
  if (p >= 40) return '#E6A23C'
  return '#67C23A'
}

const loadPredict = async deviceId => {
  predict.value = null
  predictError.value = ''
  predictLoading.value = true
  try {
    // 拦截器已解包 body.data，getCranePredict 返回的即预测对象（勿再取 .data）
    const data = await getCranePredict(deviceId)
    predict.value = data
  } catch (e) {
    predictError.value = e?.response?.data?.message || '预测服务不可用（需启动 ai-server 预测服务）'
  } finally {
    predictLoading.value = false
  }
}

const progressColor = p => {
  if (p >= 90) return '#F56C6C'
  if (p >= 80) return '#E6A23C'
  return '#67C23A'
}

const riskClass = crane => (crane.momentPercent && crane.momentPercent >= 90 ? 'is-risk' : '')
const riskColor = crane => (crane.momentPercent && crane.momentPercent >= 90 ? '#F56C6C' : '#303133')

const showDetail = crane => {
  detail.value = crane
  detailVisible.value = true
  loadPredict(crane.deviceId)
}

let unsub = null

/* ===== 作业记录 ===== */
const recordVisible = ref(false)
const records = ref([])
const recordTotal = ref(0)
const recordQuery = ref({ pageNum: 1, pageSize: 10, deviceId: null })

const openRecords = () => {
  recordVisible.value = true
  loadRecords()
}
const loadRecords = async () => {
  const data = await getCraneRecords({
    pageNum: recordQuery.value.pageNum,
    pageSize: recordQuery.value.pageSize,
    deviceId: recordQuery.value.deviceId || undefined
  })
  records.value = data.records || []
  recordTotal.value = data.total || 0
}
const onRecordPage = p => {
  recordQuery.value.pageNum = p
  loadRecords()
}

onMounted(async () => {
  cranes.value = await getCraneList()
  unsub = wsClient.subscribe(data => {
    if (data.cranes) cranes.value = data.cranes
  })
})
onUnmounted(() => unsub && unsub())
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.toolbar { display: flex; justify-content: flex-end; }
.mt12 { margin-top: 12px; }
.crane-card { border-top: 3px solid #409EFF; }
.crane-card.is-risk { border-top-color: #F56C6C; }
.crane-card.is-offline { opacity: 0.65; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.name { font-size: 16px; font-weight: 600; }
.code { font-size: 12px; color: #909399; margin-top: 2px; }
.moment-box { margin: 14px 0 6px; }
.moment-label { font-size: 12px; color: #909399; }
.moment-value { font-size: 30px; font-weight: 700; }
.moment-value .unit { font-size: 14px; font-weight: 400; }
.moment-sub { font-size: 12px; color: #909399; margin-top: 4px; }
.predict-box { background: #f8fafc; border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px; }
.predict-title { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 13px; margin-bottom: 10px; color: #303133; }
.ml8 { margin-left: 8px; }
.predict-tip { color: #909399; font-size: 13px; padding: 6px 0; }
.risk-bar { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.risk-label { font-size: 12px; color: #909399; width: 60px; }
.risk-val { font-size: 14px; font-weight: 600; color: #303133; width: 56px; text-align: right; }
.advice { margin-top: 8px; font-size: 12px; color: #606266; background: #fff; border-radius: 6px; padding: 8px 10px; line-height: 1.7; }
</style>
