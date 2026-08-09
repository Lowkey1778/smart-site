<template>
  <div>
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ==================== 设备台账（T-08/T-12/T-13） ==================== -->
        <el-tab-pane label="设备台账" name="ledger">
          <div class="toolbar">
            <el-input v-model="filters.keyword" placeholder="搜索设备名称/编码/品牌" clearable
              style="width: 220px" @keyup.enter="loadLedger" @clear="loadLedger" />
            <el-cascader v-model="filters.typeId" :options="typeOptions" placeholder="设备类型"
              clearable filterable style="width: 180px" :props="{ value: 'id', label: 'typeName', children: 'children' }"
              @change="loadLedger" />
            <el-cascader v-model="filters.locationId" :options="locationOptions" placeholder="安装位置"
              clearable filterable style="width: 180px" :props="{ value: 'id', label: 'locationName', children: 'children' }"
              @change="loadLedger" />
            <el-select v-model="filters.status" placeholder="运行状态" clearable style="width: 120px" @change="loadLedger">
              <el-option label="在线" :value="1" />
              <el-option label="离线" :value="0" />
            </el-select>
            <el-button type="primary" class="ml8" @click="loadLedger">查询</el-button>
            <el-button type="success" class="ml8" v-permission="'sys:device:add'" @click="openEdit(null)">新增设备</el-button>
          </div>

          <el-table :data="ledger" v-loading="loading" border stripe>
            <el-table-column prop="deviceCode" label="设备编码" width="105" />
            <el-table-column prop="deviceName" label="设备名称" min-width="130" />
            <el-table-column prop="typeName" label="类型" width="105" />
            <el-table-column prop="locationName" label="位置" width="110" show-overflow-tooltip />
            <el-table-column prop="brand" label="品牌" width="90" />
            <el-table-column prop="model" label="型号" width="90" />
            <el-table-column label="运行状态" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '在线' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用状态" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enableStatus === 1 ? 'primary' : 'info'" size="small">
                  {{ row.enableStatus === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="originalValue" label="原值(元)" width="100" align="right" />
            <el-table-column label="操作" min-width="210" align="center" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
                  <el-button link type="success" size="small" v-permission="'sys:device:edit'" @click="openEdit(row)">编辑</el-button>
                  <el-button link type="warning" size="small" @click="openQrcode(row)">二维码</el-button>
                  <el-button link type="danger" size="small" v-permission="'sys:device:delete'" @click="remove(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="mt12" background layout="total, prev, pager, next" :total="total"
            :page-size="filters.pageSize" v-model:current-page="filters.pageNum" @current-change="loadLedger" />
        </el-tab-pane>

        <!-- ==================== 设备类型管理（T-06） ==================== -->
        <el-tab-pane v-if="hasPerm('sys:device:type')" label="设备类型" name="type">
          <div class="tree-pane">
            <div class="tree-pane-head">
              <span class="pane-title">设备分类（多级嵌套）</span>
              <div>
                <el-button type="success" size="small" v-permission="'sys:device:type'" @click="openTypeEdit(null)">新增根类型</el-button>
              </div>
            </div>
            <el-tree :data="typeTree" node-key="id" default-expand-all :props="{ label: 'typeName', children: 'children' }">
              <template #default="{ node, data }">
                <span class="tree-node">
                  <span>{{ data.typeName }}</span>
                  <span class="tree-actions">
                    <el-button link type="primary" size="small" v-permission="'sys:device:type'" @click="openTypeEdit(data, true)">新增子级</el-button>
                    <el-button link type="warning" size="small" v-permission="'sys:device:type'" @click="openTypeEdit(data)">编辑</el-button>
                    <el-button link type="danger" size="small" v-permission="'sys:device:type'" @click="removeType(data)">删除</el-button>
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </el-tab-pane>

        <!-- ==================== 设备位置管理（T-07） ==================== -->
        <el-tab-pane v-if="hasPerm('sys:device:location')" label="设备位置" name="location">
          <div class="tree-pane">
            <div class="tree-pane-head">
              <span class="pane-title">工地区域/位置层级</span>
              <el-button type="success" size="small" v-permission="'sys:device:location'" @click="openLocEdit(null)">新增根位置</el-button>
            </div>
            <el-tree :data="locationTree" node-key="id" default-expand-all :props="{ label: 'locationName', children: 'children' }">
              <template #default="{ data }">
                <span class="tree-node">
                  <span>{{ data.locationName }}</span>
                  <span class="tree-actions">
                    <el-button link type="primary" size="small" v-permission="'sys:device:location'" @click="openLocEdit(data, true)">新增子级</el-button>
                    <el-button link type="warning" size="small" v-permission="'sys:device:location'" @click="openLocEdit(data)">编辑</el-button>
                    <el-button link type="danger" size="small" v-permission="'sys:device:location'" @click="removeLoc(data)">删除</el-button>
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ============ 设备新增/编辑弹窗（基本信息 + 全生命周期 T-12） ============ -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑设备' : '新增设备'" width="720px" destroy-on-close>
      <el-tabs v-model="editTab">
        <el-tab-pane label="基本信息" name="base">
          <el-form :model="form" label-width="100px">
            <el-row>
              <el-col :span="12">
                <el-form-item label="设备编码" required>
                  <el-input v-model="form.deviceCode" :disabled="!!form.id" placeholder="唯一编码，创建后不可修改" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="设备名称" required>
                  <el-input v-model="form.deviceName" placeholder="设备名称" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="设备类型">
                  <el-cascader v-model="form.typeId" :options="typeOptions" clearable filterable style="width: 100%"
                    :props="{ value: 'id', label: 'typeName', children: 'children', emitPath: false }" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="安装位置">
                  <el-cascader v-model="form.locationId" :options="locationOptions" clearable filterable style="width: 100%"
                    :props="{ value: 'id', label: 'locationName', children: 'children', emitPath: false }" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="品牌/厂家"><el-input v-model="form.brand" /></el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="型号"><el-input v-model="form.model" /></el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="供应商"><el-input v-model="form.supplier" /></el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="设备原值(元)">
                  <el-input-number v-model="form.originalValue" :min="0" :precision="2" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="平面坐标"><el-input v-model="form.coordinate" placeholder="如 x:120,y:80" /></el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="启用状态">
                  <el-radio-group v-model="form.enableStatus">
                    <el-radio :value="1">启用</el-radio>
                    <el-radio :value="0">禁用</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="全生命周期" name="lifecycle">
          <el-form :model="form" label-width="110px">
            <el-alert type="info" :closable="false" class="mb12"
              title="生产 → 供货 → 验收 → 安装 → 启用 → 预计报废 → 实际报废；填写启用日期与设计使用年限后自动计算预计报废日期。" />
            <el-row>
              <el-col :span="12"><el-form-item label="生产日期"><el-date-picker v-model="form.produceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="供货日期"><el-date-picker v-model="form.supplyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            </el-row>
            <el-row>
              <el-col :span="12"><el-form-item label="验收日期"><el-date-picker v-model="form.acceptDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="安装日期"><el-date-picker v-model="form.installDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            </el-row>
            <el-row>
              <el-col :span="12"><el-form-item label="启用日期"><el-date-picker v-model="form.enableDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="设计使用年限(年)"><el-input-number v-model="form.designServiceLife" :min="0" style="width: 100%" /></el-form-item></el-col>
            </el-row>
            <el-row>
              <el-col :span="12"><el-form-item label="预计报废日期"><el-date-picker v-model="form.expectScrapDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="实际报废日期"><el-date-picker v-model="form.actualScrapDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="最近维修日期"><el-date-picker v-model="form.lastMaintainDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 类型/位置编辑弹窗 ============ -->
    <el-dialog v-model="typeVisible" :title="typeForm.id ? '编辑类型' : '新增类型'" width="420px" destroy-on-close>
      <el-form :model="typeForm" label-width="80px">
        <el-form-item label="类型名称" required><el-input v-model="typeForm.typeName" /></el-form-item>
        <el-form-item label="上级类型">
          <el-cascader v-model="typeForm.parentId" :options="typeOptions" clearable filterable style="width: 100%"
            :props="{ value: 'id', label: 'typeName', children: 'children', emitPath: false, checkStrictly: true }"
            placeholder="留空为根类型" />
        </el-form-item>
        <el-form-item label="排序号"><el-input-number v-model="typeForm.sort" :min="0" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="locVisible" :title="locForm.id ? '编辑位置' : '新增位置'" width="420px" destroy-on-close>
      <el-form :model="locForm" label-width="80px">
        <el-form-item label="位置名称" required><el-input v-model="locForm.locationName" /></el-form-item>
        <el-form-item label="上级位置">
          <el-cascader v-model="locForm.parentId" :options="locationOptions" clearable filterable style="width: 100%"
            :props="{ value: 'id', label: 'locationName', children: 'children', emitPath: false, checkStrictly: true }"
            placeholder="留空为根位置" />
        </el-form-item>
        <el-form-item label="排序号"><el-input-number v-model="locForm.sort" :min="0" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitLoc">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 设备二维码弹窗（T-13） ============ -->
    <el-dialog v-model="qrVisible" title="设备二维码" width="380px" destroy-on-close>
      <div id="qr-print-area" class="qr-area">
        <div class="qr-canvas-wrap"><canvas ref="qrCanvasRef" /></div>
        <div class="qr-info">
          <div class="qr-name">{{ qrDevice.deviceName }}</div>
          <div class="qr-code">{{ qrDevice.deviceCode }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="qrVisible = false">关闭</el-button>
        <el-button type="primary" @click="printQrcode">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import {
  getDevicePage, addDevice, updateDevice, deleteDevice,
  getDeviceTypeTree, addDeviceType, updateDeviceType, deleteDeviceType,
  getDeviceLocationTree, addDeviceLocation, updateDeviceLocation, deleteDeviceLocation
} from '../api/device'

const router = useRouter()
const activeTab = ref('ledger')

const hasPerm = perm => {
  try { return JSON.parse(localStorage.getItem('perms') || '[]').includes(perm) } catch (e) { return false }
}

// ============ 台账 ============
const ledger = ref([])
const total = ref(0)
const loading = ref(false)
const filters = reactive({ pageNum: 1, pageSize: 10, keyword: '', typeId: null, locationId: null, status: null })

const loadLedger = async () => {
  loading.value = true
  try {
    const data = await getDevicePage({
      pageNum: filters.pageNum, pageSize: filters.pageSize, keyword: filters.keyword || undefined,
      typeId: filters.typeId || undefined, locationId: filters.locationId || undefined,
      status: filters.status === null || filters.status === '' ? undefined : filters.status
    })
    ledger.value = data.records
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

// ============ 类型/位置树数据 ============
const typeTree = ref([])
const locationTree = ref([])
const typeOptions = ref([])
const locationOptions = ref([])

const loadTrees = async () => {
  typeTree.value = await getDeviceTypeTree()
  locationTree.value = await getDeviceLocationTree()
  typeOptions.value = typeTree.value
  locationOptions.value = locationTree.value
}

// ============ 设备新增/编辑 ============
const editVisible = ref(false)
const editTab = ref('base')
const submitting = ref(false)
const form = ref({})

const emptyForm = () => ({
  id: null, deviceCode: '', deviceName: '', typeId: null, locationId: null,
  brand: '', model: '', supplier: '', qrCode: '', originalValue: null, coordinate: '',
  status: 0, enableStatus: 1, remark: '',
  produceDate: null, supplyDate: null, acceptDate: null, installDate: null, enableDate: null,
  designServiceLife: null, expectScrapDate: null, actualScrapDate: null, lastMaintainDate: null
})

const openEdit = row => {
  form.value = row ? { ...emptyForm(), ...row } : emptyForm()
  editTab.value = 'base'
  editVisible.value = true
}

const submitEdit = async () => {
  if (!form.value.deviceCode || !form.value.deviceName) {
    ElMessage.warning('请填写设备编码和名称')
    return
  }
  submitting.value = true
  try {
    if (form.value.id) {
      await updateDevice(form.value.id, form.value)
    } else {
      await addDevice(form.value)
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    loadLedger()
  } finally {
    submitting.value = false
  }
}

const remove = async row => {
  await ElMessageBox.confirm(`确定删除设备「${row.deviceName}」吗？其监测点将同步删除，历史告警/离线记录保留。`, '提示', { type: 'warning' })
  await deleteDevice(row.id)
  ElMessage.success('已删除')
  loadLedger()
}

const goDetail = row => router.push(`/device/${row.id}`)

// ============ 类型管理 ============
const typeVisible = ref(false)
const typeForm = ref({})

const openTypeEdit = (data, asChild = false) => {
  typeForm.value = data
    ? { id: data.id, typeName: data.typeName, parentId: asChild ? data.id : data.parentId, sort: data.sort || 0 }
    : { id: null, typeName: '', parentId: null, sort: 0 }
  typeVisible.value = true
}

const submitType = async () => {
  if (!typeForm.value.typeName) return ElMessage.warning('请填写类型名称')
  submitting.value = true
  try {
    const payload = { ...typeForm.value, parentId: typeForm.value.parentId || 0 }
    if (payload.id) await updateDeviceType(payload.id, payload)
    else await addDeviceType(payload)
    ElMessage.success('保存成功')
    typeVisible.value = false
    loadTrees()
  } finally {
    submitting.value = false
  }
}

const removeType = async data => {
  await ElMessageBox.confirm(`确定删除类型「${data.typeName}」吗？`, '提示', { type: 'warning' })
  await deleteDeviceType(data.id)
  ElMessage.success('已删除')
  loadTrees()
}

// ============ 位置管理 ============
const locVisible = ref(false)
const locForm = ref({})

const openLocEdit = (data, asChild = false) => {
  locForm.value = data
    ? { id: data.id, locationName: data.locationName, parentId: asChild ? data.id : data.parentId, sort: data.sort || 0 }
    : { id: null, locationName: '', parentId: null, sort: 0 }
  locVisible.value = true
}

const submitLoc = async () => {
  if (!locForm.value.locationName) return ElMessage.warning('请填写位置名称')
  submitting.value = true
  try {
    const payload = { ...locForm.value, parentId: locForm.value.parentId || 0 }
    if (payload.id) await updateDeviceLocation(payload.id, payload)
    else await addDeviceLocation(payload)
    ElMessage.success('保存成功')
    locVisible.value = false
    loadTrees()
  } finally {
    submitting.value = false
  }
}

const removeLoc = async data => {
  await ElMessageBox.confirm(`确定删除位置「${data.locationName}」吗？`, '提示', { type: 'warning' })
  await deleteDeviceLocation(data.id)
  ElMessage.success('已删除')
  loadTrees()
}

// ============ 二维码（T-13） ============
const qrVisible = ref(false)
const qrDevice = ref({})
const qrCanvasRef = ref()

const openQrcode = async row => {
  qrDevice.value = row
  qrVisible.value = true
  await nextTick()
  const content = `建筑安全智能监控平台\n设备:${row.deviceName}\n编码:${row.deviceCode}\n位置:${row.locationName || ''}`
  QRCode.toCanvas(qrCanvasRef.value, content, { width: 220, margin: 1 })
}

const printQrcode = () => {
  const srcCanvas = qrCanvasRef.value
  const printWin = window.open('', '_blank', 'width=420,height=540')
  printWin.document.write(
    '<html><head><title>设备二维码</title></head>' +
    '<body style="font-family:Microsoft YaHei;text-align:center;padding:30px">' +
    '<canvas id="q" width="220" height="220"></canvas>' +
    '<div style="font-size:18px;font-weight:700;margin:8px 0">' + qrDevice.value.deviceName + '</div>' +
    '<div style="color:#666">' + qrDevice.value.deviceCode + '</div>' +
    '</body></html>')
  printWin.document.close()
  printWin.document.getElementById('q').getContext('2d').drawImage(srcCanvas, 0, 0, 220, 220)
  setTimeout(() => { printWin.focus(); printWin.print() }, 200)
}

onMounted(async () => {
  loadLedger()
  await loadTrees()
})

watch(activeTab, tab => {
  if (tab === 'type' || tab === 'location') loadTrees()
})
</script>

<style scoped>
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.row-actions { display: flex; align-items: center; justify-content: center; white-space: nowrap; }
.row-actions .el-button + .el-button { margin-left: 2px; }
.ml8 { margin-left: 8px; }
.mt12 { margin-top: 12px; }
.tree-pane { max-height: 560px; overflow: auto; }
.tree-pane-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.pane-title { font-weight: 600; color: #303133; }
.tree-node { display: inline-flex; align-items: center; }
.tree-actions { margin-left: 12px; opacity: 0; transition: opacity .2s; }
.tree-node:hover .tree-actions { opacity: 1; }
.qr-area { text-align: center; padding: 8px; }
.qr-canvas-wrap { display: inline-block; border: 1px dashed #dcdfe6; padding: 10px; border-radius: 6px; }
.qr-info { margin-top: 10px; }
.qr-name { font-size: 16px; font-weight: 700; }
.qr-code { color: #909399; font-size: 13px; margin-top: 2px; }
.mb12 { margin-bottom: 12px; }
</style>
