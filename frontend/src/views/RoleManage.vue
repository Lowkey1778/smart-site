<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><UserFilled /></el-icon> 角色管理</span>
          <el-button type="success" v-permission="'sys:role:add'" @click="openEdit(null)">新增角色</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="roleCode" label="角色编码" width="130" />
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="description" label="角色描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="菜单权限数" width="100" align="center">
          <template #default="{ row }">{{ (row.menuIds || []).length }} 项</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" v-permission="'sys:role:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" v-permission="'sys:role:delete'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑角色' : '新增角色'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.roleCode" :disabled="!!form.id" placeholder="如 SAFETY、MANAGER（创建后不可修改）" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" placeholder="角色名称" />
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="角色职责描述" />
        </el-form-item>
        <el-form-item label="角色状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单权限">
          <div class="tree-box">
            <el-tree ref="menuTreeRef" :data="menuTree" node-key="id" show-checkbox default-expand-all
              :props="{ label: 'menuName', children: 'children' }">
              <template #default="{ data }">
                <span class="tree-node">
                  <span>{{ data.menuName }}</span>
                  <el-tag v-if="data.menuType === 3" size="small" type="warning" class="btn-tag">按钮</el-tag>
                </span>
              </template>
            </el-tree>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleList, getMenuTree, addRole, updateRole, deleteRole } from '../api/sys'

const list = ref([])
const menuTree = ref([])
const loading = ref(false)
const submitting = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    list.value = await getRoleList()
  } finally {
    loading.value = false
  }
}

// 新增/编辑
const editVisible = ref(false)
const form = ref({})
const menuTreeRef = ref()

const emptyForm = () => ({ id: null, roleCode: '', roleName: '', description: '', status: 1, menuIds: [] })

const openEdit = async row => {
  form.value = row ? { ...emptyForm(), ...row, menuIds: [...(row.menuIds || [])] } : emptyForm()
  editVisible.value = true
  await nextTick()
  // 回显勾选（父节点自动半选）
  menuTreeRef.value && menuTreeRef.value.setCheckedKeys(form.value.menuIds)
}

const collectMenuIds = () => {
  if (!menuTreeRef.value) return []
  // 已勾选 + 半选（父目录）构成完整权限集
  return [...menuTreeRef.value.getCheckedKeys(), ...menuTreeRef.value.getHalfCheckedKeys()]
}

const submitEdit = async () => {
  if (!form.value.roleCode || !form.value.roleName) {
    ElMessage.warning('请填写角色编码和名称')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form.value, menuIds: collectMenuIds() }
    if (form.value.id) {
      await updateRole(form.value.id, payload)
    } else {
      await addRole(payload)
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

const remove = async row => {
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？将同步解除该角色的用户关联与菜单权限。`, '提示', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(async () => {
  loadList()
  menuTree.value = await getMenuTree()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.tree-box {
  width: 100%; max-height: 300px; overflow-y: auto;
  border: 1px solid #e4e7ed; border-radius: 4px; padding: 6px;
}
.tree-node { display: inline-flex; align-items: center; }
.btn-tag { margin-left: 6px; transform: scale(0.85); }
</style>
