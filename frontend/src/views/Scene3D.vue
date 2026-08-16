<template>
  <div class="scene-wrap">
    <div ref="container" class="canvas-box"></div>

    <!-- HUD 左侧：设备实时数据 -->
    <div class="hud hud-left">
      <div class="hud-title">🏗 塔吊实时状态</div>
      <div v-for="c in cranes" :key="c.deviceId" class="hud-item" :class="{ 'hud-alarm': isCraneRisk(c) }">
        <div class="hud-name">{{ c.deviceName }}
          <el-tag v-if="isCraneRisk(c)" type="danger" size="small" effect="dark" class="alarm-tag">🚨 {{ riskDesc(c) }}</el-tag>
        </div>
        <div class="hud-line">力矩 {{ fmt(c.moment) }} t·m · 载荷 <b :style="{ color: pctColor(c.momentPercent) }">{{ fmt(c.momentPercent) }}%</b></div>
        <div class="hud-line">吊钩 {{ fmt(c.height) }}m · 回转 {{ fmt(c.angle) }}° · 幅度 {{ fmt(c.radiusVal) }}m</div>
        <div class="hud-line" :class="{ 'wind-alarm': windRisk(c) }">风速 {{ fmt(c.windSpeed) }} m/s{{ windRisk(c) ? ' ⚠ 超标' : '' }}</div>
      </div>
      <div class="hud-title lift-title">🛗 升降机运行</div>
      <div v-for="l in lifts" :key="l.deviceId" class="hud-item">
        <div class="hud-name">{{ l.deviceName }}</div>
        <div class="hud-line">载重 {{ fmt(l.loadWeight) }}kg ({{ fmt(l.loadPercent) }}%) · {{ l.direction === 1 ? '▲ 上升' : '▼ 下降' }}</div>
        <div class="hud-line">高度 {{ fmt(l.height) }}m · {{ l.personCount }} 人</div>
      </div>
    </div>

    <!-- HUD 右侧：环境数据 -->
    <div class="hud hud-right">
      <div class="hud-title">🌤 环境监测</div>
      <div v-for="e in env" :key="e.pointId" class="hud-env">
        <span class="env-name">{{ e.pointName }}</span>
        <b :style="{ color: envColor(e) }">{{ fmt(e.value) }} <small>{{ e.unit || '' }}</small></b>
      </div>
    </div>

    <div class="hud-tip">🖱 左键旋转视角 · 滚轮缩放 · 右键平移</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { getCraneList, getLiftList, getEnvPoints } from '../api/monitor'
import wsClient from '../api/ws'

const container = ref(null)
const cranes = ref([])
const lifts = ref([])
const env = ref([])

const fmt = v => (v === null || v === undefined ? '-' : Number(v).toLocaleString())
const pctColor = p => (p >= 90 ? '#F56C6C' : p >= 75 ? '#E6A23C' : '#67C23A')
// 塔吊告警判定：力矩占比 ≥90%（超限）或风速 ≥18m/s（警报级大风）
const isCraneRisk = c => (c.momentPercent && Number(c.momentPercent) >= 90) || windRisk(c)
const windRisk = c => c.windSpeed != null && Number(c.windSpeed) >= 18
/** 告警原因描述（3D 告警标签/浮窗展示用） */
const riskDesc = c => {
  const reasons = []
  if (c.momentPercent && Number(c.momentPercent) >= 90) reasons.push('力矩超限')
  if (windRisk(c)) reasons.push('风速超标')
  return reasons.join('+') || '告警'
}
const envColor = e => {
  if (e.value === null || e.value === undefined) return '#fff'
  const limit = e.warnMax || e.alarmMax
  if (limit && Number(e.value) >= Number(limit)) return '#F56C6C'
  return '#7dd3fc'
}

/* ================= Three.js 场景 ================= */
let renderer, scene, camera, controls
let craneModels = {}   // deviceId -> { group, armGroup, trolley, hook, mastMat, target }
let liftModel = null   // { cage, guide, target }
let rafId = null

// 目标值（动画插值用）
const targets = { crane1: {}, crane2: {}, lift: {} }

/** 创建标准材质 */
const mat = (color, opts = {}) => new THREE.MeshStandardMaterial({
  color, roughness: 0.6, metalness: 0.25, ...opts
})

/** 创建一座塔吊模型 */
function buildCrane(deviceId, x, z, frontArmLen) {
  const group = new THREE.Group()
  group.position.set(x, 0, z)

  // 基座
  const base = new THREE.Mesh(new THREE.BoxGeometry(4, 1.2, 4), mat(0x4a5568))
  base.position.y = 0.6
  group.add(base)

  // 塔身（高 80m）
  const mastH = 80
  const mastMat = mat(0xf59e0b)
  const mast = new THREE.Mesh(new THREE.BoxGeometry(1.4, mastH, 1.4), mastMat)
  mast.position.y = mastH / 2
  group.add(mast)

  // 回转平台
  const platform = new THREE.Mesh(new THREE.BoxGeometry(3, 1.2, 3), mat(0x374151))
  platform.position.y = mastH + 0.6
  group.add(platform)

  // 回转组：吊臂 + 平衡臂 + 塔帽 + 小车 + 吊钩
  const armGroup = new THREE.Group()
  armGroup.position.y = mastH + 1.2

  // 吊臂（朝 +X）
  const boomLen = frontArmLen || 60
  const boom = new THREE.Mesh(new THREE.BoxGeometry(boomLen, 0.7, 0.7), mat(0xf59e0b))
  boom.position.x = boomLen / 2 - 3
  armGroup.add(boom)
  // 吊臂拉杆（简化：细线）
  const tie = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 14), mat(0x9ca3af))
  tie.rotation.z = Math.PI / 2
  tie.position.set(boomLen / 2 - 3, 6, 0)
  armGroup.add(tie)

  // 平衡臂（朝 -X）
  const jibLen = 22
  const jib = new THREE.Mesh(new THREE.BoxGeometry(jibLen, 0.6, 0.6), mat(0xf59e0b))
  jib.position.x = -jibLen / 2
  armGroup.add(jib)
  // 配重块
  const cw = new THREE.Mesh(new THREE.BoxGeometry(5, 1.6, 2.4), mat(0x64748b))
  cw.position.set(-jibLen + 2, -1.6, 0)
  armGroup.add(cw)

  // 塔帽
  const cap = new THREE.Mesh(new THREE.ConeGeometry(1.6, 9, 4), mat(0xf59e0b))
  cap.position.y = 8
  cap.rotation.y = Math.PI / 4
  armGroup.add(cap)

  // ===== 告警信标（红色旋转警示灯，告警时点亮并旋转） =====
  const beaconMat = new THREE.MeshStandardMaterial({
    color: 0xef4444, emissive: 0xef4444, emissiveIntensity: 0,
    roughness: 0.3, metalness: 0.1, transparent: true, opacity: 0.9
  })
  const beacon = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.5, 1.1, 12), beaconMat)
  beacon.position.y = 10.5
  beacon.visible = false
  armGroup.add(beacon)

  // ===== 告警文字标签（Sprite，悬停塔吊上方："🚨 塔吊告警"） =====
  const labelCanvas = document.createElement('canvas')
  labelCanvas.width = 384
  labelCanvas.height = 96
  const lctx = labelCanvas.getContext('2d')
  const roundRect = (x, y, w, h, r) => {
    if (lctx.roundRect) lctx.roundRect(x, y, w, h, r)
    else lctx.rect(x, y, w, h)
  }
  lctx.fillStyle = 'rgba(220,38,38,0.92)'
  lctx.beginPath()
  roundRect(8, 8, 368, 80, 18)
  lctx.fill()
  lctx.strokeStyle = '#fff'
  lctx.lineWidth = 3
  lctx.beginPath()
  roundRect(8, 8, 368, 80, 18)
  lctx.stroke()
  lctx.fillStyle = '#fff'
  lctx.font = 'bold 44px "Microsoft YaHei", sans-serif'
  lctx.textAlign = 'center'
  lctx.textBaseline = 'middle'
  lctx.fillText('🚨 塔吊告警', 192, 50)
  const labelTex = new THREE.CanvasTexture(labelCanvas)
  const labelSprite = new THREE.Sprite(new THREE.SpriteMaterial({
    map: labelTex, transparent: true, depthTest: false
  }))
  labelSprite.position.y = 20
  labelSprite.scale.set(16, 4, 1)
  labelSprite.visible = false
  armGroup.add(labelSprite)

  // 小车（沿吊臂移动）
  const trolley = new THREE.Mesh(new THREE.BoxGeometry(1.6, 0.8, 1.6), mat(0xef4444))
  trolley.position.set(20, 0, 0)
  armGroup.add(trolley)

  // 吊钩（线缆 + 吊钩块）
  const hook = new THREE.Group()
  const cableGeo = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(0, -0.4, 0),
    new THREE.Vector3(0, -30, 0)
  ])
  const cable = new THREE.Line(cableGeo, new THREE.LineBasicMaterial({ color: 0x9ca3af }))
  hook.add(cable)
  const hookBox = new THREE.Mesh(new THREE.BoxGeometry(0.7, 1.2, 0.7), mat(0xef4444))
  hookBox.position.y = -31
  hook.add(hookBox)
  hook.position.x = 20
  armGroup.add(hook)

  group.add(armGroup)
  scene.add(group)

  craneModels[deviceId] = { group, armGroup, trolley, hook, mastMat, beacon, beaconMat, labelSprite }
}

/** 创建升降机模型 */
function buildLift(x, z) {
  const group = new THREE.Group()
  group.position.set(x, 0, z)
  // 导轨
  const guideH = 100
  const guide = new THREE.Mesh(new THREE.BoxGeometry(0.8, guideH, 0.8), mat(0x9ca3af))
  guide.position.y = guideH / 2
  group.add(guide)
  // 轿厢
  const cage = new THREE.Mesh(new THREE.BoxGeometry(3.2, 3.4, 3.2), mat(0x3b82f6))
  cage.position.y = 3
  group.add(cage)
  // 顶架
  const top = new THREE.Mesh(new THREE.BoxGeometry(1.6, 0.6, 1.6), mat(0x374151))
  top.position.y = 4.2
  group.add(top)

  scene.add(group)
  liftModel = { group, cage }
}

/** 创建建筑楼体 */
function buildBuildings() {
  const addBuilding = (x, z, w, h, d, color) => {
    const body = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(color, { transparent: true, opacity: 0.85 }))
    body.position.set(x, h / 2, z)
    scene.add(body)
    // 楼层线框
    const edges = new THREE.EdgesGeometry(new THREE.BoxGeometry(w, h, d))
    const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: 0x334155 }))
    line.position.set(x, h / 2, z)
    scene.add(line)
  }
  addBuilding(0, -38, 34, 52, 34, 0x94a3b8)      // 1号楼
  addBuilding(48, -28, 22, 36, 22, 0x7c8ba1)     // 2号楼
}

/** 初始化场景 */
function initScene() {
  const el = container.value
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0b1220)
  scene.fog = new THREE.Fog(0x0b1220, 180, 320)

  camera = new THREE.PerspectiveCamera(55, el.clientWidth / el.clientHeight, 0.1, 600)
  camera.position.set(75, 55, 95)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(el.clientWidth, el.clientHeight)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  el.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.target.set(0, 22, 0)
  controls.maxPolarAngle = Math.PI / 2.05
  controls.minDistance = 20
  controls.maxDistance = 260

  // 灯光
  const ambient = new THREE.AmbientLight(0xffffff, 0.55)
  scene.add(ambient)
  const sun = new THREE.DirectionalLight(0xffffff, 1.6)
  sun.position.set(80, 120, 60)
  sun.castShadow = true
  scene.add(sun)
  const fill = new THREE.DirectionalLight(0x7dd3fc, 0.35)
  fill.position.set(-60, 40, -40)
  scene.add(fill)

  // 地面网格
  const grid = new THREE.GridHelper(240, 24, 0x1e3a5f, 0x16283f)
  grid.position.y = 0.02
  scene.add(grid)
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(240, 240),
    new THREE.MeshStandardMaterial({ color: 0x111c2e, roughness: 0.9 })
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = 0
  ground.receiveShadow = true
  scene.add(ground)

  // 场景元素
  buildBuildings()
  buildCrane(1, -28, 6, 60)   // 1号塔吊（frontArmLen 60m）
  buildCrane(2, 42, 10, 45)   // 2号塔吊（45m）
  buildLift(-5, 30)

  // 场景边界光柱（氛围）
  const addPole = (x, z) => {
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.15, 4), mat(0xf59e0b))
    pole.position.set(x, 2, z)
    scene.add(pole)
  }
  addPole(-60, -60); addPole(60, -60); addPole(-60, 60); addPole(60, 60)

  // 点击拾取（高亮塔吊信息由 HUD 承担，此处仅保持交互提示）
  const raycaster = new THREE.Raycaster()
  const mouse = new THREE.Vector2()
  renderer.domElement.addEventListener('click', e => {
    mouse.x = (e.clientX / el.clientWidth) * 2 - 1
    mouse.y = -(e.clientY / el.clientHeight) * 2 + 1
    raycaster.setFromCamera(mouse, camera)
    const meshes = []
    scene.traverse(o => { if (o.isMesh) meshes.push(o) })
    const hits = raycaster.intersectObjects(meshes, false)
    if (hits.length) {
      // 找到所属塔吊并触发闪烁
      let g = hits[0].object
      while (g && !g.userData.craneId) g = g.parent
      if (g && g.userData.craneId) {
        const model = craneModels[g.userData.craneId]
        if (model) model.pulse = 1
      }
    }
  })

  window.addEventListener('resize', onResize)
  animate()
}

function onResize() {
  const el = container.value
  if (!el || !renderer) return
  camera.aspect = el.clientWidth / el.clientHeight
  camera.updateProjectionMatrix()
  renderer.setSize(el.clientWidth, el.clientHeight)
}

/** 平滑插值 */
const lerp = (cur, target, k = 0.12) => cur + (target - cur) * k

/** 动画循环 */
function animate() {
  rafId = requestAnimationFrame(animate)
  const t = performance.now() / 1000

  // 塔吊动画
  for (const [id, model] of Object.entries(craneModels)) {
    const c = cranes.value.find(x => String(x.deviceId) === String(id))
    const tg = targets['crane' + id] || {}
    if (c) {
      tg.angle = Number(c.angle) || 0
      tg.radius = Math.max(2, Math.min(Number(c.radiusVal) || 20, (model.armLen || 60) - 2))
      tg.height = Math.max(1, Number(c.height) || 5)
      tg.risk = isCraneRisk(c) ? 1 : 0
    } else {
      tg.risk = 0
    }
    // 回转
    model.armGroup.rotation.y = lerp(model.armGroup.rotation.y, THREE.MathUtils.degToRad(tg.angle || 0), 0.1)
    // 小车沿吊臂
    model.trolley.position.x = lerp(model.trolley.position.x, tg.radius || 20, 0.1)
    // 吊钩：线缆 + 吊钩块（吊臂面 0，向下 = 塔顶-吊钩高度）
    model.hook.position.x = model.trolley.position.x
    const cableY = -((model.mastH || 80) - (tg.height || 5))
    model.hook.children[1].position.y = lerp(model.hook.children[1].position.y, cableY, 0.1)
    // 告警闪烁
    const pulse = Math.sin(t * 6) * 0.35 + 0.65
    if (tg.risk) {
      model.mastMat.color.setHex(0xef4444)
      model.mastMat.emissive.setRGB(pulse, 0, 0)
      model.mastMat.emissiveIntensity = 0.8
      // 3D 告警：红色信标旋转警示 + 悬浮告警标签
      if (model.beacon) {
        model.beacon.visible = true
        model.beacon.rotation.y = t * 8
        model.beaconMat.emissiveIntensity = pulse * 1.6
        model.beaconMat.opacity = 0.6 + pulse * 0.4
      }
      if (model.labelSprite) {
        model.labelSprite.visible = true
        model.labelSprite.position.y = 20 + Math.sin(t * 4) * 1.2   // 上下浮动
      }
    } else {
      model.mastMat.color.setHex(0xf59e0b)
      model.mastMat.emissive.setRGB(0, 0, 0)
      if (model.beacon) {
        model.beacon.visible = false
        model.beaconMat.emissiveIntensity = 0
      }
      if (model.labelSprite) model.labelSprite.visible = false
    }
  }

  // 升降机动画
  if (liftModel) {
    const l = lifts.value[0]
    const tg = targets.lift
    if (l) {
      tg.height = Math.max(2, Number(l.height) || 3)
    }
    const y = lerp(liftModel.cage.position.y, (tg.height || 3) + 1.7, 0.1)
    liftModel.cage.position.y = y
  }

  controls.update()
  renderer.render(scene, camera)
}

/* ================= 数据加载 ================= */
const loadData = async () => {
  try {
    const [cs, ls] = await Promise.all([getCraneList(), getLiftList()])
    cranes.value = cs || []
    lifts.value = ls || []
  } catch (e) { /* 忽略 */ }
  try {
    env.value = await getEnvPoints()
  } catch (e) { /* 忽略 */ }
}

let unsub = null
onMounted(async () => {
  await loadData()
  initScene()
  unsub = wsClient.subscribe(data => {
    if (data.cranes) cranes.value = data.cranes
    if (data.lifts) lifts.value = data.lifts
    if (data.env) env.value = data.env
  })
})

onUnmounted(() => {
  unsub && unsub()
  if (rafId) cancelAnimationFrame(rafId)
  window.removeEventListener('resize', onResize)
  if (renderer) {
    renderer.dispose()
    if (renderer.domElement && renderer.domElement.parentNode) {
      renderer.domElement.parentNode.removeChild(renderer.domElement)
    }
  }
})
</script>

<style scoped>
.scene-wrap { position: relative; height: calc(100vh - 110px); min-height: 560px; }
.canvas-box { width: 100%; height: 100%; border-radius: 8px; overflow: hidden; }

.hud {
  position: absolute;
  top: 12px;
  background: rgba(8, 18, 34, 0.82);
  border: 1px solid #1e3a5f;
  border-radius: 8px;
  padding: 10px 12px;
  color: #cbd5e1;
  font-size: 12px;
  backdrop-filter: blur(6px);
  max-height: calc(100% - 40px);
  overflow-y: auto;
}
.hud-left { left: 12px; width: 250px; }
.hud-right { right: 12px; width: 200px; }
.hud-title { font-weight: 600; color: #7dd3fc; margin-bottom: 6px; }
.lift-title { margin-top: 12px; }
.hud-item { margin-bottom: 8px; padding: 6px 8px; background: rgba(30, 58, 95, 0.4); border-radius: 6px; border-left: 3px solid #1e3a5f; }
.hud-item.hud-alarm { border-left-color: #ef4444; background: rgba(239, 68, 68, 0.15); }
.alarm-tag { margin-left: 2px; animation: alarm-blink 1s infinite; }
.wind-alarm { color: #f56c6c !important; font-weight: 600; }
@keyframes alarm-blink { 0%, 100% { opacity: 1; } 50% { opacity: .5; } }
.hud-name { font-weight: 600; color: #e2e8f0; margin-bottom: 2px; display: flex; align-items: center; gap: 6px; }
.hud-line { color: #94a3b8; line-height: 1.7; }
.hud-env { display: flex; justify-content: space-between; gap: 8px; padding: 3px 0; border-bottom: 1px dashed #1e3a5f; }
.hud-env:last-child { border-bottom: none; }
.env-name { color: #94a3b8; }
.hud-env b { color: #7dd3fc; font-weight: 600; }
.hud-tip {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(8, 18, 34, 0.7);
  border-radius: 20px;
  padding: 5px 16px;
  color: #64748b;
  font-size: 12px;
}
::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-thumb { background: #1e3a5f; border-radius: 2px; }
</style>
