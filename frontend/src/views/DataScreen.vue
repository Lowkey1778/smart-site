<template>
  <div class="screen">
    <!-- 顶部标题栏 -->
    <div class="screen-header">
      <div class="title">建筑安全智能监控平台 · 综合安全态势大屏</div>
      <div class="clock">{{ now }}</div>
    </div>

    <div class="screen-body">
      <!-- 左列 -->
      <div class="col">
        <div class="panel">
          <div class="panel-title">设备状态总览</div>
          <div class="device-summary">
            <div class="ds-item">
              <div class="ds-value" style="color:#67C23A">{{ overview.deviceSummary ? overview.deviceSummary.online : 0 }}</div>
              <div class="ds-label">在线设备</div>
            </div>
            <div class="ds-item">
              <div class="ds-value" style="color:#F56C6C">{{ overview.deviceSummary ? overview.deviceSummary.offline : 0 }}</div>
              <div class="ds-label">离线设备</div>
            </div>
            <div class="ds-item">
              <div class="ds-value" style="color:#409EFF">{{ overview.deviceSummary ? overview.deviceSummary.total : 0 }}</div>
              <div class="ds-label">设备总数</div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">告警汇总</div>
          <div class="alarm-summary">
            <div class="as-item" v-for="item in alarmSummaryList" :key="item.label">
              <div class="as-value" :style="{ color: item.color }">{{ item.value }}</div>
              <div class="as-label">{{ item.label }}</div>
            </div>
          </div>
        </div>

        <div class="panel grow">
          <div class="panel-title">塔吊运行概览</div>
          <div class="crane-mini" v-for="c in overview.cranes || []" :key="c.deviceId">
            <div class="cm-name">{{ c.deviceName }}</div>
            <div class="cm-bar">
              <div class="cm-fill" :style="{ width: percent(c.momentPercent) + '%', background: c.momentPercent >= 90 ? '#F56C6C' : '#409EFF' }"></div>
            </div>
            <div class="cm-val">{{ fmt(c.momentPercent) }}%</div>
          </div>
          <div v-if="!(overview.cranes || []).length" class="empty">暂无数据</div>
        </div>
        <div class="panel">
          <div class="panel-title">升降机运行概览</div>
          <div class="lift-mini" v-for="l in overview.lifts || []" :key="l.deviceId">
            <div class="lm-head">
              <span class="lm-name">{{ l.deviceName }}</span>
              <span class="lm-info">{{ fmt(l.height) }}m · {{ l.direction === 1 ? '上升' : '下降' }} · {{ l.personCount || 0 }}人</span>
            </div>
            <div class="cm-bar">
              <div class="cm-fill" :style="{ width: percent(l.loadPercent) + '%', background: l.loadPercent >= 90 ? '#F56C6C' : '#67C23A' }"></div>
            </div>
            <div class="cm-val">{{ fmt(l.loadPercent) }}%</div>
          </div>
          <div v-if="!(overview.lifts || []).length" class="empty">暂无数据</div>
        </div>
      </div>

      <!-- 中列 -->
      <div class="col">
        <div class="panel grow">
          <div class="panel-title">告警趋势（近7天）</div>
          <div ref="trendRef" class="chart"></div>
        </div>
        <div class="panel">
          <div class="panel-title">告警级别分布</div>
          <div ref="levelRef" class="chart chart-sm"></div>
        </div>
      </div>

      <!-- 右列 -->
      <div class="col">
        <div class="panel">
          <div class="panel-title">环境空气质量</div>
          <div class="env-grid">
            <div class="env-item" v-for="p in (overview.env || []).slice(0, 6)" :key="p.pointId">
              <div class="env-name">{{ p.pointName }}</div>
              <div class="env-val" :style="{ color: envColor(p) }">{{ fmt(p.value) }}<span class="unit">{{ p.unit }}</span></div>
            </div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-title">视频监控</div>
          <div class="cam-grid">
            <div class="cam-card" v-for="c in cameras" :key="c.cameraId">
              <div class="cam-thumb">
                <img v-if="c.thumbUrl" :src="c.thumbUrl" alt="" />
                <div v-else class="cam-placeholder"><el-icon :size="26"><VideoCamera /></el-icon></div>
                <span class="cam-dot" :class="c.onlineStatus === 1 ? 'on' : 'off'"></span>
              </div>
              <div class="cam-name">{{ c.cameraName }}</div>
              <div class="cam-tags">
                <span v-if="c.aiHelmet === 1" class="cam-tag">安全帽</span>
                <span v-if="c.aiVest === 1" class="cam-tag">安全服</span>
                <span v-if="c.aiSmoke === 1" class="cam-tag">吸烟</span>
                <span v-if="c.aiFire === 1" class="cam-tag">明火</span>
              </div>
            </div>
          </div>
        </div>
        <div class="panel grow">
          <div class="panel-title">最新告警</div>
          <div class="alarm-scroll">
            <div class="alarm-item" v-for="a in latestAlarms" :key="a.id">
              <span class="level-dot" :style="{ background: levelColor(a.alarmLevel) }"></span>
              <span class="alarm-text">{{ a.alarmContent }}</span>
              <span class="alarm-time">{{ a.alarmTime.slice(11, 19) }}</span>
            </div>
            <div v-if="!latestAlarms.length" class="empty">暂无告警</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getDashboardOverview, getAlarmList } from '../api/monitor'
import request from '../api/request'
import wsClient from '../api/ws'

const overview = ref({})
const latestAlarms = ref([])
const cameras = ref([])
const now = ref(new Date().toLocaleTimeString('zh-CN'))
const trendRef = ref()
const levelRef = ref()
let trendChart = null
let levelChart = null

const fmt = v => (v === null || v === undefined ? '-' : Number(v).toLocaleString())
const percent = p => Math.min(100, Number(p || 0))
const levelColor = l => ['', '#E6A23C', '#F56C6C', '#b71c1c'][l] || '#E6A23C'
const envColor = p => {
  if (p.value === null) return '#fff'
  const v = Number(p.value)
  if (p.alarmMax != null && v > Number(p.alarmMax)) return '#F56C6C'
  if (p.warnMax != null && v > Number(p.warnMax)) return '#E6A23C'
  return '#67C23A'
}

const alarmSummaryList = computed(() => {
  const s = overview.value.alarmSummary || {}
  return [
    { label: '未处置', value: s.unhandled || 0, color: '#F56C6C' },
    { label: '处置中', value: s.handling || 0, color: '#E6A23C' },
    { label: '已处置', value: s.handled || 0, color: '#67C23A' }
  ]
})

const renderTrend = data => {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const days = data.map(d => d.day)
  const counts = data.map(d => Number(d.cnt))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 24 },
    xAxis: { type: 'category', data: days, axisLabel: { color: '#8aa', fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { color: '#8aa' }, splitLine: { lineStyle: { color: '#ffffff22' } } },
    series: [{
      type: 'bar', data: counts, barWidth: '45%',
      itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

const renderLevel = data => {
  if (!levelRef.value) return
  if (!levelChart) levelChart = echarts.init(levelRef.value)
  const names = { 1: '预警', 2: '警报', 3: '控制' }
  levelChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#8aa', fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      data: data.map(d => ({ name: names[d.level] || d.level, value: Number(d.cnt) })),
      label: { color: '#ccd', fontSize: 11 },
      itemStyle: { borderColor: '#0a1628', borderWidth: 2 }
    }]
  })
}

const loadThumbs = async () => {
  cameras.value = await request.get('/camera/thumbs')
}

const loadAll = async () => {
  overview.value = await getDashboardOverview()
  loadThumbs()
  const alarmData = await getAlarmList({ pageNum: 1, pageSize: 8, handleStatus: 0 })
  latestAlarms.value = alarmData.records
  renderTrend(overview.value.alarmTrend || [])
  renderLevel(overview.value.byLevel || [])
}

const clockTimer = setInterval(() => { now.value = new Date().toLocaleTimeString('zh-CN') }, 1000)
const resize = () => { trendChart && trendChart.resize(); levelChart && levelChart.resize() }

let unsub = null
onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', resize)
  unsub = wsClient.subscribe(() => loadAll())
})
onUnmounted(() => {
  clearInterval(clockTimer)
  unsub && unsub()
  window.removeEventListener('resize', resize)
  trendChart && trendChart.dispose()
  levelChart && levelChart.dispose()
})
</script>

<style scoped>
.screen {
  background: #0a1628;
  color: #fff;
  min-height: calc(100vh - 100px);
  padding: 16px;
  border-radius: 8px;
}
.screen-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 12px 14px;
  border-bottom: 1px solid #ffffff1a;
}
.title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(90deg, #409EFF, #7dd3fc);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.clock { font-size: 16px; color: #8aa; font-family: monospace; }
.screen-body {
  display: grid;
  grid-template-columns: 1fr 1.2fr 1fr;
  gap: 12px;
  margin-top: 12px;
}
.col { display: flex; flex-direction: column; gap: 12px; }
.panel {
  background: #0e1e36;
  border: 1px solid #ffffff14;
  border-radius: 8px;
  padding: 12px;
}
.panel.grow { flex: 1; }
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #7dd3fc;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #409EFF;
}
.device-summary, .alarm-summary { display: flex; justify-content: space-around; }
.ds-item, .as-item { text-align: center; }
.ds-value, .as-value { font-size: 30px; font-weight: 700; }
.ds-label, .as-label { color: #8aa; font-size: 12px; margin-top: 2px; }
.crane-mini { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.cm-name { width: 70px; font-size: 12px; color: #ccd; }
.cm-bar { flex: 1; height: 10px; background: #ffffff14; border-radius: 5px; overflow: hidden; }
.cm-fill { height: 100%; border-radius: 5px; transition: width 0.5s; }
.cm-val { width: 44px; font-size: 12px; color: #ccd; text-align: right; }
.chart { height: 240px; }
.chart-sm { height: 180px; }
.env-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.env-item { text-align: center; background: #ffffff0a; border-radius: 6px; padding: 8px 4px; }
.env-name { font-size: 12px; color: #8aa; }
.env-val { font-size: 20px; font-weight: 700; margin-top: 4px; }
.env-val .unit { font-size: 11px; color: #8aa; margin-left: 2px; }
.alarm-scroll { max-height: 300px; overflow-y: auto; }
.alarm-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 4px;
  border-bottom: 1px solid #ffffff0d;
  font-size: 12px;
}
.level-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.alarm-text { flex: 1; color: #ccd; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alarm-time { color: #8aa; font-family: monospace; }
.empty { color: #556; text-align: center; padding: 20px 0; font-size: 13px; }
.lift-mini { margin-bottom: 8px; }
.lm-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.lm-name { font-size: 12px; color: #ccd; }
.lm-info { font-size: 11px; color: #8aa; }
.cam-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.cam-card { background: #ffffff0a; border-radius: 6px; overflow: hidden; }
.cam-thumb { position: relative; height: 74px; background: #0a1628; }
.cam-thumb img { width: 100%; height: 100%; object-fit: cover; }
.cam-placeholder { height: 100%; display: flex; align-items: center; justify-content: center; color: #556; }
.cam-dot { position: absolute; top: 6px; right: 6px; width: 8px; height: 8px; border-radius: 50%; }
.cam-dot.on { background: #67C23A; box-shadow: 0 0 6px #67C23A; }
.cam-dot.off { background: #F56C6C; }
.cam-name { font-size: 12px; color: #ccd; padding: 6px 8px 2px; }
.cam-tags { padding: 0 8px 6px; display: flex; gap: 4px; flex-wrap: wrap; }
.cam-tag { font-size: 10px; color: #7dd3fc; background: #409EFF22; border-radius: 3px; padding: 1px 5px; }


</style>
