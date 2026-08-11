<template>
  <div>
    <!-- 1. 数据概览统计卡片 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><HomeFilled /></el-icon> 数据概览首页</span>
          <el-tag type="success" size="small">今日安全态势 · 自动刷新</el-tag>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :xs="12" :md="6" v-for="item in stats" :key="item.label">
          <div class="stat-card" :style="{ borderColor: item.color }">
            <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 2. 关键指标快速入口（T-04 / RQ-05） -->
    <el-card shadow="never" class="mt16">
      <template #header>
        <div class="card-header">
          <span><el-icon><Opportunity /></el-icon> 关键指标快速入口</span>
          <el-tag type="info" size="small">点击卡片直达对应监控页面</el-tag>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :md="8" v-for="item in quickEntries" :key="item.path">
          <div class="quick-card" @click="go(item.path)">
            <div class="quick-icon" :style="{ background: item.color }">
              <el-icon :size="26"><component :is="item.icon" /></el-icon>
            </div>
            <div class="quick-info">
              <div class="quick-title">{{ item.title }}</div>
              <div class="quick-desc">{{ item.desc }}</div>
              <div class="quick-link">点击查看 <el-icon><ArrowRight /></el-icon></div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 3. 角色差异化内容（T-05 / RQ-05）：领导看汇总、所有角色看当前登录用户；无汇总权限时不占位 -->
    <!-- 安全态势汇总 与 当前登录用户 卡片宽度统一由 colWidth 控制，始终等宽 -->
    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :md="colWidth" v-if="showSummary">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><DataLine /></el-icon> 安全态势汇总</span>
              <el-button size="small" type="primary" @click="go('/screen')">查看数据大屏</el-button>
            </div>
          </template>
          <el-row :gutter="12">
            <el-col :xs="12" :md="6" v-for="item in summaryCards" :key="item.label">
              <div class="stat-card" :style="{ borderColor: item.color }">
                <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
                <div class="stat-label">{{ item.label }}</div>
              </div>
            </el-col>
          </el-row>
          <div class="mt12 summary-tip">
            <el-icon><InfoFilled /></el-icon>
            告警及时处置率与趋势详见「数据大屏」与「告警统计分析」页面
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="colWidth">
        <el-card shadow="never">
          <template #header>
            <span><el-icon><User /></el-icon> 当前登录用户</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ userInfo.realName }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ roleName }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ userInfo.dept || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 4. 最新告警 + 待办事项（安全员/管理员看待办；无待办权限时最新告警占满整行） -->
    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :md="showTodo ? 12 : 24">
        <el-card shadow="never">
          <template #header>
            <span><el-icon><Bell /></el-icon> 最新告警</span>
          </template>
          <el-table :data="latestAlarms" size="small" stripe>
            <el-table-column prop="alarmTime" label="时间" width="160" />
            <el-table-column prop="alarmContent" label="告警内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="级别" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="['', 'warning', 'danger', 'error'][row.alarmLevel]" size="small">
                  {{ ['', '预警', '警报', '控制'][row.alarmLevel] }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12" v-if="showTodo">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><List /></el-icon> 待办事项 · 未处理告警</span>
              <el-button size="small" @click="go('/alarm')">告警管理</el-button>
            </div>
          </template>
          <el-table :data="todoAlarms" size="small" stripe>
            <el-table-column prop="alarmTime" label="时间" width="155" />
            <el-table-column prop="alarmContent" label="告警内容" min-width="150" show-overflow-tooltip />
            <el-table-column label="级别" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="['', 'warning', 'danger', 'error'][row.alarmLevel]" size="small">
                  {{ ['', '预警', '警报', '控制'][row.alarmLevel] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="85" align="center">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="go('/alarm')">去处置</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!todoAlarms.length" class="empty-tip">暂无未处理告警，现场安全 👍</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats, getDashboardOverview, getAlarmList } from '../api/monitor'
import request from '../api/request'
import wsClient from '../api/ws'

const router = useRouter()
const go = path => router.push(path)

const userInfo = ref({})
const roleName = ref('')
const roles = ref([])

const stats = ref([
  { label: '在线设备', value: '-', color: '#67C23A' },
  { label: '离线设备', value: '-', color: '#F56C6C' },
  { label: '今日告警', value: '-', color: '#E6A23C' },
  { label: '未处理告警', value: '-', color: '#409EFF' }
])
const latestAlarms = ref([])

/* ================= T-04 关键指标快速入口 ================= */
const overview = ref({ cranes: [], lifts: [], env: [] })

const craneOnline = computed(() => overview.value.cranes.filter(c => c.status === 1).length)
const craneAlarm = computed(() => overview.value.cranes.filter(c =>
  c.loadVal != null && c.ratedLoad > 0 && c.loadVal / c.ratedLoad >= 0.9).length)
const liftOnline = computed(() => overview.value.lifts.filter(l => l.status === 1).length)
const liftOverload = computed(() => overview.value.lifts.filter(l =>
  l.loadWeight != null && l.ratedLoad > 0 && l.loadWeight / l.ratedLoad >= 0.9).length)
const envOverload = computed(() => overview.value.env.filter(e =>
  e.value != null && ((e.warnMax != null && e.value > e.warnMax) || (e.warnMin != null && e.value < e.warnMin))).length)

const quickEntries = computed(() => [
  {
    title: '塔吊运行状态', icon: 'Odometer', color: '#409EFF', path: '/crane',
    desc: `在线 ${craneOnline.value} 台 · 载重超限 ${craneAlarm.value} 台`
  },
  {
    title: '升降机运行状态', icon: 'OfficeBuilding', color: '#67C23A', path: '/lift',
    desc: `在线 ${liftOnline.value} 台 · 载重超限 ${liftOverload.value} 台`
  },
  {
    title: '环境空气质量', icon: 'Sunny', color: '#E6A23C', path: '/env',
    desc: `监测点 ${overview.value.env.length} 个 · 超标 ${envOverload.value} 个`
  }
])

/* ================= T-05 角色差异化内容 ================= */
const isAdmin = computed(() => roles.value.includes('ADMIN'))
const isLeader = computed(() => roles.value.includes('LEADER'))
const isSafety = computed(() => roles.value.includes('SAFETY'))
const showSummary = computed(() => isAdmin.value || isLeader.value)
const showTodo = computed(() => isAdmin.value || isSafety.value)
/** 安全态势汇总 与 当前登录用户 卡片共用同一宽度（有汇总权限时各半行并排，无权限时当前登录用户占满） */
const colWidth = computed(() => (showSummary.value ? 12 : 24))

const alarmSummary = ref({ unhandled: 0, handling: 0, handled: 0, total: 0 })
const summaryCards = computed(() => [
  { label: '告警总数', value: alarmSummary.value.total, color: '#606266' },
  { label: '未处置', value: alarmSummary.value.unhandled, color: '#F56C6C' },
  { label: '处置中', value: alarmSummary.value.handling, color: '#E6A23C' },
  { label: '已处置', value: alarmSummary.value.handled, color: '#67C23A' }
])
const todoAlarms = ref([])

/* ================= 数据加载 ================= */
const loadAll = async () => {
  try {
    const data = await getDashboardStats()
    const labels = ['在线设备', '离线设备', '今日告警', '未处理告警']
    const keys = ['onlineDevices', 'offlineDevices', 'todayAlarms', 'unhandledAlarms']
    stats.value = keys.map((k, i) => ({ label: labels[i], value: data[k], color: stats.value[i].color }))
    const alarms = await getAlarmList({ pageNum: 1, pageSize: 6 })
    latestAlarms.value = alarms.records

    const ov = await getDashboardOverview()
    overview.value = ov
    alarmSummary.value = ov.alarmSummary || { unhandled: 0, handling: 0, handled: 0, total: 0 }

    if (showTodo.value) {
      const todo = await getAlarmList({ pageNum: 1, pageSize: 8, handleStatus: 0 })
      todoAlarms.value = todo.records || []
    }
  } catch (e) {
    // 错误已由拦截器统一提示
  }
}

let unsub = null
onMounted(async () => {
  const stored = JSON.parse(localStorage.getItem('userInfo') || '{}')
  roles.value = stored.roles || []
  userInfo.value = await request.get('/auth/info')
  const map = { ADMIN: '系统管理员', LEADER: '项目经理', SAFETY: '安全管理员' }
  roleName.value = roles.value.map(r => map[r] || r).join(' / ')
  await loadAll()
  unsub = wsClient.subscribe(() => loadAll())
})
onUnmounted(() => unsub && unsub())
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.stat-card { text-align: center; padding: 20px 0; border: 2px solid; border-radius: 8px; background: #fff; }
.stat-value { font-size: 32px; font-weight: 700; }
.stat-label { margin-top: 6px; color: #606266; font-size: 14px; }
.mt16 { margin-top: 16px; }
.mt12 { margin-top: 12px; }

.quick-card {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 16px; border: 1px solid #e4e7ed; border-radius: 8px;
  cursor: pointer; transition: all .2s; background: #fff;
}
.quick-card:hover { border-color: #409eff; box-shadow: 0 4px 14px rgba(64, 158, 255, .18); transform: translateY(-2px); }
.quick-icon {
  width: 52px; height: 52px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; color: #fff;
}
.quick-title { font-size: 15px; font-weight: 600; color: #303133; }
.quick-desc { margin-top: 4px; font-size: 13px; color: #606266; }
.quick-link { margin-top: 6px; font-size: 12px; color: #409eff; display: flex; align-items: center; gap: 2px; }

.summary-tip { font-size: 12px; color: #909399; display: flex; align-items: center; gap: 4px; }
.empty-tip { padding: 18px 0; text-align: center; color: #909399; font-size: 13px; }
</style>