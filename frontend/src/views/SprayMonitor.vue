<template>
  <div>
    <el-alert type="info" :closable="false" class="mb16"
      title="喷淋降尘：设备状态卡片快捷开关 / 定时任务 / 自动联动（PM2.5超标自动喷淋），全部操作留痕可追溯" />

    <el-tabs v-model="tab">
      <!-- ============ 喷淋设备状态（卡片式主界面） ============ -->
      <el-tab-pane label="设备状态" name="status">
        <el-row :gutter="16">
          <el-col :span="8" v-for="d in sprayDevices" :key="d.id">
            <el-card shadow="hover" class="spray-card" :class="{ 'is-spraying': d.spraying }">
              <template #header>
                <div class="spray-card-head">
                  <div class="spray-name">
                    <el-icon :size="20" color="#409EFF"><Umbrella /></el-icon>
                    <span>{{ d.deviceName }}</span>
                    <el-tag v-if="d.spraying" type="success" size="small" effect="dark">喷淋中</el-tag>
                    <el-tag v-else type="info" size="small">已停止</el-tag>
                  </div>
                  <el-tag :type="d.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
                    {{ d.status === 1 ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </template>
              <div class="spray-card-body">
                <div class="spray-meta">
                  <span class="meta-label">设备编码</span><span>{{ d.deviceCode }}</span>
                </div>
                <div class="spray-meta">
                  <span class="meta-label">安装位置</span><span>{{ d.locationName }}</span>
                </div>
                <div class="spray-meta">
                  <span class="meta-label">启用状态</span>
                  <el-tag :type="d.enableStatus === 1 ? 'primary' : 'info'" size="small">
                    {{ d.enableStatus === 1 ? '启用' : '禁用' }}
                  </el-tag>
                </div>

                <!-- 周边湿度（核心指标） -->
                <div class="env-block">
                  <div class="env-title"><el-icon><Odometer /></el-icon> 周边环境</div>
                  <div class="env-item">
                    <span class="env-label">湿度</span>
                    <el-progress :percentage="humidityPct(d.humidity)" :stroke-width="10"
                      :color="humidityColor(d.humidity)" />
                    <span class="env-value"><b>{{ d.humidity ?? '-' }}</b> {{ d.humidityUnit || '%RH' }}</span>
                  </div>
                  <div class="env-item">
                    <span class="env-label">PM2.5</span>
                    <el-progress :percentage="pm25Pct(d.pm25)" :stroke-width="10" :color="pm25Color(d.pm25)" />
                    <span class="env-value"><b>{{ d.pm25 ?? '-' }}</b> {{ d.pm25Unit || 'μg/m³' }}</span>
                  </div>
                  <div class="env-item env-note" v-if="d.envDeviceName">
                    <span class="env-label">数据来源</span><span>{{ d.envDeviceName }}（同位置环境监测站）</span>
                  </div>
                </div>

                <div class="spray-meta" v-if="d.lastSprayTime">
                  <span class="meta-label">最近操作</span><span>{{ d.lastSprayTime }} · {{ d.spraying ? '开启' : '关闭' }}</span>
                </div>
                <div class="spray-meta" v-if="d.lastReason">
                  <span class="meta-label">操作原因</span><span class="reason">{{ d.lastReason }}</span>
                </div>

                <!-- 快捷控制（UC-003 开启/关闭互斥：开启仅关闭时可点，关闭仅喷淋中时可点） -->
                <div class="spray-actions">
                  <el-button type="success" size="small" :loading="d._loading" :disabled="!!d.spraying"
                    v-permission="'sys:spray:control'" @click="quickControl(d, 1)">
                    <el-icon><VideoPlay /></el-icon>&nbsp;开启喷淋
                  </el-button>
                  <el-button type="warning" size="small" :loading="d._loading" :disabled="!d.spraying"
                    v-permission="'sys:spray:control'" @click="quickControl(d, 2)">
                    <el-icon><VideoPause /></el-icon>&nbsp;关闭喷淋
                  </el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-if="!sprayDevices.length" description="暂未配置喷淋设备（设备类型为「喷淋设备」的资产）" />
      </el-tab-pane>

      <!-- ============ T-24 操作记录 ============ -->
      <el-tab-pane label="操作记录" name="record">
        <el-card shadow="never">
          <el-form :inline="true" class="mb12">
            <el-form-item label="位置">
              <el-select v-model="query.locationId" placeholder="全部位置" clearable style="width: 140px" @change="loadRecords">
                <el-option v-for="l in locations" :key="l.id" :label="l.locationName" :value="l.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="触发方式">
              <el-select v-model="query.triggerType" placeholder="全部" clearable style="width: 120px" @change="loadRecords">
                <el-option label="手动" :value="1" />
                <el-option label="定时任务" :value="2" />
                <el-option label="自动联动" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="操作类型">
              <el-select v-model="query.action" placeholder="全部" clearable style="width: 110px" @change="loadRecords">
                <el-option label="开启" :value="1" />
                <el-option label="关闭" :value="2" />
              </el-select>
            </el-form-item>
          </el-form>

          <el-table :data="records" size="small" border stripe>
            <el-table-column prop="createTime" label="操作时间" width="170" />
            <el-table-column prop="locationName" label="喷淋位置" width="120" />
            <el-table-column label="触发方式" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="['', 'primary', 'warning', 'success'][row.triggerType]" size="small">
                  {{ ['', '手动', '定时任务', '自动联动'][row.triggerType] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.action === 1 ? 'success' : 'info'" size="small">{{ row.action === 1 ? '开启' : '关闭' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因/说明" min-width="220" show-overflow-tooltip />
            <el-table-column prop="operator" label="操作人" width="100" align="center" />
          </el-table>
          <el-pagination class="mt12" background layout="total, prev, pager, next" :total="total"
            :page-size="query.pageSize" :current-page="query.pageNum" @current-change="onPageChange" />
        </el-card>
      </el-tab-pane>

      <!-- ============ T-25 定时任务管理 ============ -->
      <el-tab-pane label="定时任务" name="task">
        <el-card shadow="never">
          <el-button type="primary" class="mb12" v-permission="'sys:spray:task'" @click="openTaskEdit(null)">
            <el-icon><Plus /></el-icon>&nbsp;新增定时任务
          </el-button>
          <el-table :data="tasks" size="small" border stripe>
            <el-table-column prop="taskName" label="任务名称" width="140" />
            <el-table-column prop="locationName" label="喷淋位置" width="110" />
            <el-table-column prop="startTime" label="开始时间" width="100" align="center" />
            <el-table-column prop="duration" label="时长(分钟)" width="90" align="center" />
            <el-table-column label="周期" width="110" align="center">
              <template #default="{ row }">每 {{ row.periodValue }} {{ row.periodUnit === 'day' ? '天' : '周' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" v-permission="'sys:spray:task'" @click="openTaskEdit(row)">编辑</el-button>
                <el-button link :type="row.status === 1 ? 'warning' : 'success'" size="small" v-permission="'sys:spray:task'" @click="toggleTask(row)">
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" size="small" v-permission="'sys:spray:task'" @click="removeTask(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

    </el-tabs>

    <!-- 定时任务编辑弹窗 -->
    <el-dialog v-model="taskDialogVisible" :title="editingTaskId ? '编辑定时任务' : '新增定时任务'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.taskName" placeholder="如：每日上午降尘" />
        </el-form-item>
        <el-form-item label="喷淋位置" required>
          <el-select v-model="taskForm.locationId" placeholder="选择位置" style="width: 100%">
            <el-option v-for="l in locations" :key="l.id" :label="l.locationName" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-select v-model="taskForm.startTime" start="00:00" step="00:30" end="23:30" placeholder="选择时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="持续时长" required>
          <el-input-number v-model="taskForm.duration" :min="1" :max="180" /> <span class="ml8">分钟</span>
        </el-form-item>
        <el-form-item label="执行周期">
          <el-input-number v-model="taskForm.periodValue" :min="1" :max="7" />
          <el-select v-model="taskForm.periodUnit" style="width: 100px; margin-left: 8px">
            <el-option label="天" value="day" />
            <el-option label="周" value="week" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="taskForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTask">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSprayRecords, getSprayTasks, addSprayTask, updateSprayTask, deleteSprayTask,
  changeSprayTaskStatus, sprayManual, getSprayLocations, getSprayStatus
} from '../api/spray'

const tab = ref('status')

/* ==================== 喷淋设备状态（卡片式主界面） ==================== */
const sprayDevices = ref([])

const humidityPct = v => (v === null || v === undefined ? 0 : Math.max(0, Math.min(100, Math.round(Number(v)))))
const pm25Pct = v => (v === null || v === undefined ? 0 : Math.max(0, Math.min(100, Math.round((Number(v) / 200) * 100))))
const humidityColor = v => {
  if (v === null || v === undefined) return '#c0c4cc'
  const n = Number(v)
  if (n < 30) return '#E6A23C'   // 干燥
  if (n > 85) return '#67C23A'   // 湿度过高（利于降尘）
  return '#409EFF'
}
const pm25Color = v => {
  if (v === null || v === undefined) return '#c0c4cc'
  const n = Number(v)
  if (n >= 150) return '#F56C6C' // 警报
  if (n >= 75) return '#E6A23C'  // 预警
  return '#67C23A'
}

const loadSprayStatus = async () => {
  try {
    sprayDevices.value = await getSprayStatus()
  } catch (e) { /* 错误已由拦截器提示 */ }
}

/** 喷淋控制唯一入口（设备状态卡片快捷开关）校验位置 → loading → 调接口 → 成功提示 → 刷新状态与记录 */
const controlSpray = async (locationId, action, reason) => {
  if (!locationId) {
    ElMessage.warning('请选择喷淋位置')
    return false
  }
  try {
    await sprayManual({ locationId, action, reason })
    ElMessage.success(action === 1 ? '喷淋已开启' : '喷淋已关闭')
    loadSprayStatus()
    loadRecords()
    return true
  } catch (e) {
    return false
  }
}

/** 卡片快捷开启/关闭喷淋（UC-003 互斥：开启仅关闭时可点，关闭仅喷淋中时可点） */
const quickControl = async (d, action) => {
  d._loading = true
  try {
    await controlSpray(d.locationId, action, (action === 1 ? '卡片快捷开启' : '卡片快捷关闭') + '喷淋')
  } finally {
    d._loading = false
  }
}

/* ==================== 操作记录（T-24） ==================== */
const locations = ref([])
const records = ref([])
const total = ref(0)
const query = ref({ pageNum: 1, pageSize: 10, locationId: null, triggerType: null, action: null })

const loadRecords = async () => {
  const data = await getSprayRecords({
    pageNum: query.value.pageNum, pageSize: query.value.pageSize,
    locationId: query.value.locationId || undefined,
    triggerType: query.value.triggerType ?? undefined,
    action: query.value.action ?? undefined
  })
  records.value = data.records || []
  total.value = data.total || 0
}
const onPageChange = page => {
  query.value.pageNum = page
  loadRecords()
}

/* ==================== 定时任务（T-25） ==================== */
const tasks = ref([])
const taskDialogVisible = ref(false)
const editingTaskId = ref(null)
const taskForm = ref({})

const loadTasks = async () => {
  tasks.value = await getSprayTasks()
}
const openTaskEdit = row => {
  if (row) {
    editingTaskId.value = row.id
    taskForm.value = { ...row }
  } else {
    editingTaskId.value = null
    taskForm.value = { duration: 30, periodValue: 1, periodUnit: 'day', status: 1 }
  }
  taskDialogVisible.value = true
}
const submitTask = async () => {
  if (!taskForm.value.taskName || !taskForm.value.locationId || !taskForm.value.startTime || !taskForm.value.duration) {
    ElMessage.warning('请完整填写任务信息')
    return
  }
  if (editingTaskId.value) {
    await updateSprayTask(editingTaskId.value, taskForm.value)
    ElMessage.success('修改成功')
  } else {
    await addSprayTask(taskForm.value)
    ElMessage.success('新增成功')
  }
  taskDialogVisible.value = false
  loadTasks()
}
const toggleTask = async row => {
  await changeSprayTaskStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  loadTasks()
}
const removeTask = async row => {
  await ElMessageBox.confirm(`确定删除定时任务「${row.taskName}」吗？`, '提示', { type: 'warning' })
  await deleteSprayTask(row.id)
  ElMessage.success('已删除')
  loadTasks()
}

// 手动控制页面（T-26）已移除，/api/spray/manual 接口仍被设备状态卡片快捷开关复用

// 切换 Tab 时刷新对应数据（操作记录实时性）
watch(tab, val => {
  if (val === 'status') loadSprayStatus()
  else if (val === 'record') loadRecords()
  else if (val === 'task') loadTasks()
})

let timer = null
onMounted(async () => {
  locations.value = await getSprayLocations()
  loadSprayStatus()
  loadRecords()
  loadTasks()
  // 每 10 秒静默刷新设备状态（喷淋中/周边湿度实时变化），每 30 秒刷新记录
  timer = setInterval(() => {
    loadSprayStatus()
    loadRecords()
    loadTasks()
  }, 10000)
})
onUnmounted(() => {
  timer && clearInterval(timer)
})
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.mb12 { margin-bottom: 12px; }
.mt12 { margin-top: 12px; }
.ml8 { margin-left: 8px; }

/* ===== 喷淋设备状态卡片 ===== */
.spray-card { margin-bottom: 16px; transition: all .25s; }
.spray-card.is-spraying { border: 1px solid #67c23a; box-shadow: 0 4px 14px rgba(103, 194, 58, .25); }
.spray-card-head { display: flex; justify-content: space-between; align-items: center; }
.spray-name { display: flex; align-items: center; gap: 8px; font-weight: 600; color: #303133; }
.spray-card-body { padding: 2px 0; }
.spray-meta { display: flex; justify-content: space-between; gap: 8px; font-size: 13px; color: #606266; line-height: 1.9; }
.spray-meta .meta-label { color: #909399; flex-shrink: 0; }
.spray-meta .reason { color: #909399; text-align: right; }
.env-block {
  margin: 10px 0; padding: 10px 12px; background: #f7f9fc; border-radius: 8px;
  border: 1px solid #ebeef5;
}
.env-title { font-size: 12px; color: #909399; margin-bottom: 8px; display: flex; align-items: center; gap: 4px; }
.env-item { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.env-item:last-child { margin-bottom: 0; }
.env-label { width: 52px; font-size: 13px; color: #606266; flex-shrink: 0; }
.env-item .el-progress { flex: 1; }
.env-value { width: 92px; text-align: right; font-size: 13px; color: #606266; flex-shrink: 0; }
.env-value b { font-size: 15px; color: #303133; }
.env-note { font-size: 12px; color: #909399; }
.spray-actions { margin-top: 12px; display: flex; gap: 10px; }
</style>