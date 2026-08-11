<template>
  <div>
    <el-alert type="info" :closable="false" class="mb16"
      title="升降机实时监控：载重/超员/风速/门锁状态，数据每 5 秒通过 WebSocket 推送" />
    <div class="mb16 toolbar">
      <el-button type="primary" @click="openRecords"><el-icon><Document /></el-icon>&nbsp;作业记录</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :span="8" v-for="lift in lifts" :key="lift.deviceId">
        <el-card shadow="hover" :class="['lift-card', lift.status !== 1 ? 'is-offline' : '', riskClass(lift)]">
          <div class="card-head">
            <div>
              <div class="name">{{ lift.deviceName }}</div>
              <div class="code">{{ lift.deviceCode }}</div>
            </div>
            <el-tag :type="lift.status === 1 ? 'success' : 'danger'">
              {{ lift.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </div>

          <div class="load-box">
            <div class="load-label">当前载重</div>
            <div class="load-value" :style="{ color: riskColor(lift) }">
              {{ fmt(lift.loadWeight) }} <span class="unit">kg</span>
            </div>
            <el-progress :percentage="percent(lift.loadPercent)" :color="progressColor" :stroke-width="10" />
            <div class="load-sub">
              额定 {{ fmt(lift.ratedLoad) }} kg · 占比 {{ fmt(lift.loadPercent) }}%
            </div>
          </div>

          <div class="mt12">
            <el-tag :type="lift.doorFront === 1 ? 'success' : 'info'" size="small" class="door-tag">
              前门 {{ lift.doorFront === 1 ? '关闭' : '打开' }}
            </el-tag>
            <el-tag :type="lift.doorBack === 1 ? 'success' : 'info'" size="small">
              后门 {{ lift.doorBack === 1 ? '关闭' : '打开' }}
            </el-tag>
            <el-tag v-if="lift.doorFront === 0 && lift.doorBack === 0" type="danger" size="small" class="door-tag">
              双门同开！
            </el-tag>
          </div>

          <el-descriptions :column="3" size="small" class="mt12">
            <el-descriptions-item label="载人数">{{ lift.personCount }} 人</el-descriptions-item>
            <el-descriptions-item label="高度">{{ fmt(lift.height) }} m</el-descriptions-item>
            <el-descriptions-item label="方向">{{ lift.direction === 1 ? '上升' : lift.direction === 2 ? '下降' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="风速">{{ fmt(lift.windSpeed) }} m/s</el-descriptions-item>
            <el-descriptions-item label="提升速度">{{ fmt(lift.liftSpeed) }} m/s</el-descriptions-item>
            <el-descriptions-item label="最大高度">{{ fmt(lift.maxLiftHeight) }} m</el-descriptions-item>
          </el-descriptions>

          <el-button type="primary" size="small" class="mt12" @click="showDetail(lift)">查看详情</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="detailVisible" :title="detail && detail.deviceName" width="520px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备编码">{{ detail.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="运行状态">
            <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small">
              {{ detail.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="额定重量">{{ fmt(detail.ratedWeight || detail.ratedLoad) }} kg</el-descriptions-item>
          <el-descriptions-item label="最大提升高度">{{ fmt(detail.maxLiftHeight) }} m</el-descriptions-item>
          <el-descriptions-item label="提升速度">{{ fmt(detail.liftSpeed) }} m/s</el-descriptions-item>
          <el-descriptions-item label="基础高度">{{ fmt(detail.baseHeight) }} m</el-descriptions-item>
          <el-descriptions-item label="当前载重">{{ fmt(detail.loadWeight) }} kg</el-descriptions-item>
          <el-descriptions-item label="载重占比">{{ fmt(detail.loadPercent) }}%</el-descriptions-item>
          <el-descriptions-item label="载人数">{{ detail.personCount }} 人</el-descriptions-item>
          <el-descriptions-item label="当前高度">{{ fmt(detail.height) }} m</el-descriptions-item>
          <el-descriptions-item label="前门锁">{{ detail.doorFront === 1 ? '关闭' : '打开' }}</el-descriptions-item>
          <el-descriptions-item label="后门锁">{{ detail.doorBack === 1 ? '关闭' : '打开' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="detail.loadPercent && detail.loadPercent >= 90" type="error" :closable="false"
          class="mt12" title="载重接近/超过额定值，存在超载风险！" />
      </template>
    </el-dialog>
  </div>

    <!-- 升降机作业记录弹窗（T-15 / RQ-20） -->
    <el-dialog v-model="recordVisible" title="升降机作业记录" width="920px">
      <el-form :inline="true" class="mb12">
        <el-form-item label="升降机">
          <el-select v-model="recordQuery.deviceId" placeholder="全部升降机" clearable style="width: 160px" @change="loadRecords">
            <el-option v-for="lf in lifts" :key="lf.deviceId" :label="lf.deviceName" :value="lf.deviceId" />
          </el-select>
        </el-form-item>
        <el-button size="small" @click="loadRecords">刷新</el-button>
      </el-form>
      <el-table :data="records" size="small" border stripe max-height="420">
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column prop="loadWeight" label="载重(kg)" width="95" align="center" />
        <el-table-column prop="personCount" label="人数" width="70" align="center" />
        <el-table-column label="楼层" width="100" align="center">
          <template #default="{ row }">{{ row.startFloor }} → {{ row.endFloor }}</template>
        </el-table-column>
        <el-table-column prop="windSpeed" label="风速" width="80" align="center" />
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">{{ row.direction === 1 ? '上升' : '下降' }}</template>
        </el-table-column>
        <el-table-column label="高度变化" width="120" align="center">
          <template #default="{ row }">{{ fmt(row.startHeight) }} → {{ fmt(row.endHeight) }}m</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-pagination class="mt12" background layout="total, prev, pager, next" :total="recordTotal"
        :page-size="recordQuery.pageSize" :current-page="recordQuery.pageNum" @current-change="onRecordPage" />
    </el-dialog>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getLiftList, getLiftRecords } from '../api/monitor'
import wsClient from '../api/ws'

const lifts = ref([])
const detailVisible = ref(false)
const detail = ref(null)

const fmt = v => (v === null || v === undefined ? '-' : Number(v).toLocaleString())
const percent = p => Math.min(100, Number(p || 0))
const progressColor = p => {
  if (p >= 90) return '#F56C6C'
  if (p >= 80) return '#E6A23C'
  return '#67C23A'
}
const riskClass = lift => (lift.loadPercent && lift.loadPercent >= 90 ? 'is-risk' : '')
const riskColor = lift => (lift.loadPercent && lift.loadPercent >= 90 ? '#F56C6C' : '#303133')

const showDetail = lift => {
  detail.value = lift
  detailVisible.value = true
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
  const data = await getLiftRecords({
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
  lifts.value = await getLiftList()
  unsub = wsClient.subscribe(data => {
    if (data.lifts) lifts.value = data.lifts
  })
})
onUnmounted(() => unsub && unsub())
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.toolbar { display: flex; justify-content: flex-end; }
.mt12 { margin-top: 12px; }
.lift-card { border-top: 3px solid #409EFF; }
.lift-card.is-risk { border-top-color: #F56C6C; }
.lift-card.is-offline { opacity: 0.65; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.name { font-size: 16px; font-weight: 600; }
.code { font-size: 12px; color: #909399; margin-top: 2px; }
.load-box { margin: 14px 0 6px; }
.load-label { font-size: 12px; color: #909399; }
.load-value { font-size: 30px; font-weight: 700; }
.load-value .unit { font-size: 14px; font-weight: 400; }
.load-sub { font-size: 12px; color: #909399; margin-top: 4px; }
.door-tag { margin-right: 8px; }
</style>
