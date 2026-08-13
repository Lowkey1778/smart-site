<template>
  <div>
    <el-alert type="info" :closable="false" class="mb16"
      title="视频监控：OBS/FFmpeg 推流 → nginx-rtmp 转 HLS → 页面实时播放。支持 1/4/9 分屏与全屏" />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><VideoCamera /></el-icon> 实时视频监控</span>
          <div>
            <el-radio-group v-model="layout" size="small" @change="rebuildPlayers">
              <el-radio-button :value="1">单画面</el-radio-button>
              <el-radio-button :value="4">4画面</el-radio-button>
              <el-radio-button :value="9">9画面</el-radio-button>
            </el-radio-group>
            <el-button size="small" class="ml12" @click="toggleFullscreen">
              {{ isFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
            <el-button size="small" type="primary" class="ml12" @click="manageVisible = true">摄像头管理</el-button>
          </div>
        </div>
      </template>

      <!-- 单画面：摄像头切换器 -->
      <div v-if="layout === 1 && enabledCams.length > 1" class="mb12 cam-switch">
        <el-button size="small" @click="prevCam"><el-icon><ArrowLeft /></el-icon>上一路</el-button>
        <el-select :model-value="currentCamId" size="small" style="width: 180px; margin: 0 8px" @change="switchCam">
          <el-option v-for="c in enabledCams" :key="c.id" :label="c.cameraName" :value="c.id" />
        </el-select>
        <el-button size="small" @click="nextCam">下一路<el-icon><ArrowRight /></el-icon></el-button>
        <el-tag size="small" type="info" class="ml12">{{ singleCamIndex + 1 }} / {{ enabledCams.length }}</el-tag>
      </div>

      <!-- 视频网格：key 含布局维度，切换布局强制重建 video 元素，确保 ref 回调重新触发 -->
      <div class="video-grid" :class="'grid-' + layout" ref="gridRef">
        <div class="video-cell" v-for="(cam, idx) in visibleCams" :key="layout + '-' + cam.id">
          <div class="video-title">
            <span>{{ cam.cameraName }}</span>
            <el-tag :type="cam.onlineStatus === 1 ? 'success' : 'danger'" size="small" class="status-tag">
              {{ cam.onlineStatus === 1 ? '在线' : '离线' }}
            </el-tag>
          </div>
          <video v-if="cam.onlineStatus === 1" :ref="el => setVideoRef(el, cam.id)" class="video-el" muted autoplay></video>
          <div v-else class="video-offline">
            <el-icon :size="40"><VideoCameraFilled /></el-icon>
            <p>摄像头离线</p>
          </div>
        </div>
        <!-- 空位补齐 -->
        <div class="video-cell empty-cell" v-for="n in (layout - visibleCams.length)" :key="'empty-' + n">
          <span class="empty-text">无摄像头画面</span>
        </div>
      </div>
    </el-card>

    <!-- 摄像头管理弹窗 -->
    <el-dialog v-model="manageVisible" title="摄像头管理" width="860px">
      <el-form :inline="true" class="mb12">
        <el-form-item label="名称">
          <el-input v-model="form.cameraName" placeholder="摄像头名称" style="width: 160px" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.cameraCode" placeholder="如 CAM-003" style="width: 140px" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="HLS地址">
          <el-input v-model="form.streamUrl" placeholder="http://localhost:8088/hls/cam1.m3u8" style="width: 280px" />
        </el-form-item>
        <el-form-item label="AI识别">
          <el-checkbox v-model="form.aiHelmet" :true-value="1" :false-value="0">安全帽</el-checkbox>
          <el-checkbox v-model="form.aiVest" :true-value="1" :false-value="0">安全服</el-checkbox>
          <el-checkbox v-model="form.aiSmoke" :true-value="1" :false-value="0">吸烟</el-checkbox>
          <el-checkbox v-model="form.aiFire" :true-value="1" :false-value="0">明火</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitCamera">{{ editingId ? '保存修改' : '新增摄像头' }}</el-button>
          <el-button @click="resetForm">清空</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="cameras" size="small" border stripe max-height="360">
        <el-table-column prop="cameraCode" label="编码" width="100" />
        <el-table-column prop="cameraName" label="名称" width="140" />
        <el-table-column prop="streamUrl" label="HLS地址" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.onlineStatus === 1 ? 'success' : 'danger'" size="small">
              {{ row.onlineStatus === 1 ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI识别" width="180">
          <template #default="{ row }">
            <el-tag v-if="row.aiHelmet === 1" size="small" class="ai-tag">安全帽</el-tag>
            <el-tag v-if="row.aiVest === 1" size="small" class="ai-tag">安全服</el-tag>
            <el-tag v-if="row.aiSmoke === 1" size="small" class="ai-tag">吸烟</el-tag>
            <el-tag v-if="row.aiFire === 1" size="small" class="ai-tag">明火</el-tag>
            <span v-if="!row.aiHelmet && !row.aiVest && !row.aiSmoke && !row.aiFire" class="empty-text">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="deleteCamera(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import Hls from 'hls.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

// 布局持久化：刷新/重进页面保持用户选择的 单画面/4画面/9画面
const layout = ref(Number(localStorage.getItem('videoLayout')) || 4)
watch(layout, v => localStorage.setItem('videoLayout', v))
// 单画面下当前显示的摄像头序号（支持切换）
const singleCamIndex = ref(0)
const cameras = ref([])
const manageVisible = ref(false)
const isFullscreen = ref(false)
const gridRef = ref()
const videoRefs = {}

const enabledCams = computed(() => cameras.value.filter(c => c.enableStatus === 1))
const currentCamId = computed(() => {
  const e = enabledCams.value
  return e.length ? e[Math.min(singleCamIndex.value, e.length - 1)].id : null
})
const visibleCams = computed(() => {
  const e = enabledCams.value
  if (layout.value === 1) {
    return e.length ? [e[Math.min(singleCamIndex.value, e.length - 1)]] : []
  }
  return e.slice(0, layout.value)
})

// 单画面切换：上一路 / 下一路 / 下拉选择
const prevCam = () => {
  const n = enabledCams.value.length
  if (n < 2) return
  singleCamIndex.value = (singleCamIndex.value - 1 + n) % n
  rebuildPlayers()
}
const nextCam = () => {
  const n = enabledCams.value.length
  if (n < 2) return
  singleCamIndex.value = (singleCamIndex.value + 1) % n
  rebuildPlayers()
}
const switchCam = id => {
  const idx = enabledCams.value.findIndex(c => c.id === id)
  if (idx >= 0) {
    singleCamIndex.value = idx
    rebuildPlayers()
  }
}

const setVideoRef = (el, id) => {
  if (el) videoRefs[id] = el
  else delete videoRefs[id]
}

// 播放器重建代数：防止快速切换布局时的竞态（旧任务不得操作新元素）
let rebuildGen = 0
// 每个摄像头的播放状态：camId -> { hls, playing }
const playerStates = {}
const retryTimers = {}

/**
 * 为摄像头建立/复用播放器。
 * 关键改进（修复"必须刷新页面才出画面"）：推流未就绪时持续自动重试，
 * 推流一旦就绪（OBS 开始推流）画面自动出现，无需手动刷新。
 */
const ensurePlayer = (cam, gen) => {
  const videoEl = videoRefs[cam.id]
  if (!videoEl || !cam.streamUrl) return
  const st = playerStates[cam.id] || (playerStates[cam.id] = {})
  // 已有存活播放器：由 hls 自身/错误回调负责恢复，避免重复创建
  if (st.hls && !st.hls.destroyed) return
  if (Hls.isSupported()) {
    const hls = new Hls({ liveDurationInfinity: true })
    st.hls = hls
    st.playing = false
    hls.loadSource(cam.streamUrl)
    hls.attachMedia(videoEl)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      st.playing = true
      if (gen === rebuildGen) videoEl.play().catch(() => {})
    })
    hls.on(Hls.Events.ERROR, (e, data) => {
      if (!data.fatal) return
      if (gen !== rebuildGen) return
      // 致命错误（推流未就绪/断流）：销毁后定时重建，自动等待推流就绪
      try { hls.destroy() } catch (err) { /* 忽略 */ }
      st.hls = null
      st.playing = false
      scheduleRetry(cam, gen)
    })
  } else if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
    videoEl.src = cam.streamUrl
    st.playing = true
  }
}

const scheduleRetry = (cam, gen) => {
  if (retryTimers[cam.id]) clearTimeout(retryTimers[cam.id])
  retryTimers[cam.id] = setTimeout(() => {
    if (gen === rebuildGen) ensurePlayer(cam, gen)
  }, 5000)
}

/** 周期看门狗：对未出画面的在线摄像头每 5 秒尝试恢复播放（推流就绪后自动出画面） */
let watchdogTimer = null
const startWatchdog = () => {
  if (watchdogTimer) clearInterval(watchdogTimer)
  watchdogTimer = setInterval(() => {
    const gen = rebuildGen
    visibleCams.value.forEach(cam => {
      if (cam.onlineStatus !== 1) return
      const st = playerStates[cam.id]
      if (st && st.hls && !st.hls.destroyed && st.playing) return
      ensurePlayer(cam, gen)
    })
  }, 5000)
}

const rebuildPlayers = async () => {
  const gen = ++rebuildGen
  // 销毁旧播放器
  Object.keys(playerStates).forEach(k => {
    const st = playerStates[k]
    if (st && st.hls) {
      try { st.hls.destroy() } catch (e) { /* 忽略 */ }
    }
  })
  Object.keys(playerStates).forEach(k => delete playerStates[k])
  Object.keys(retryTimers).forEach(k => {
    clearTimeout(retryTimers[k])
    delete retryTimers[k]
  })
  Object.keys(videoRefs).forEach(k => delete videoRefs[k])
  await nextTick()
  // 布局切换后 video 元素已按新 key 重建（ref 回调已重新填充 videoRefs）
  visibleCams.value.forEach(cam => ensurePlayer(cam, gen))
  startWatchdog()
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    gridRef.value.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

// 摄像头管理
const form = ref({ aiHelmet: 0, aiVest: 0, aiSmoke: 0, aiFire: 0 })
const editingId = ref(null)

const resetForm = () => {
  editingId.value = null
  form.value = { aiHelmet: 0, aiVest: 0, aiSmoke: 0, aiFire: 0 }
}

const openEdit = row => {
  editingId.value = row.id
  form.value = { ...row }
}

const submitCamera = async () => {
  if (!form.value.cameraName || !form.value.cameraCode) {
    ElMessage.warning('请填写名称和编码')
    return
  }
  if (editingId.value) {
    await request.put(`/camera/${editingId.value}`, form.value)
    ElMessage.success('已更新，重新加载播放器')
  } else {
    await request.post('/camera', form.value)
    ElMessage.success('新增成功')
  }
  resetForm()
  loadCameras()
}
const deleteCamera = async row => {
  await ElMessageBox.confirm(`确定删除摄像头「${row.cameraName}」吗？`, '提示', { type: 'warning' })
  await request.delete(`/camera/${row.id}`)
  ElMessage.success('已删除')
  loadCameras()
}

const loadCameras = async () => {
  cameras.value = await request.get('/camera/list')
  rebuildPlayers()
}

const onFullscreenChange = () => { isFullscreen.value = !!document.fullscreenElement }

onMounted(async () => {
  await loadCameras()
  document.addEventListener('fullscreenchange', onFullscreenChange)
})
onUnmounted(() => {
  if (watchdogTimer) clearInterval(watchdogTimer)
  Object.keys(playerStates).forEach(k => {
    const st = playerStates[k]
    if (st && st.hls) {
      try { st.hls.destroy() } catch (e) { /* 忽略 */ }
    }
  })
  Object.keys(retryTimers).forEach(k => clearTimeout(retryTimers[k]))
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.ml12 { margin-left: 12px; }
.cam-switch { display: flex; align-items: center; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.video-grid { display: grid; gap: 8px; background: #0a0f1a; padding: 8px; border-radius: 6px; }
.grid-1 { grid-template-columns: 1fr; }
.grid-4 { grid-template-columns: 1fr 1fr; }
.grid-9 { grid-template-columns: 1fr 1fr 1fr; }
.video-cell { position: relative; background: #000; border-radius: 4px; overflow: hidden; aspect-ratio: 16/9; }
.video-title {
  position: absolute; top: 0; left: 0; right: 0; z-index: 10;
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 10px; background: linear-gradient(180deg, #000c, transparent);
  color: #fff; font-size: 13px;
}
.status-tag { transform: scale(0.85); }
.video-el { width: 100%; height: 100%; object-fit: cover; }
.video-offline {
  height: 100%; display: flex; flex-direction: column;
  align-items: center; justify-content: center; color: #556;
}
.video-offline p { margin-top: 8px; font-size: 13px; }
.empty-cell { display: flex; align-items: center; justify-content: center; background: #0d1320; }
.empty-text { color: #556; font-size: 13px; }
.mb12 { margin-bottom: 12px; }
.ai-tag { margin-right: 4px; }
</style>
