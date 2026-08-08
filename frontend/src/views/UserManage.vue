<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><User /></el-icon> 用户管理</span>
          <div class="toolbar">
            <el-input v-model="filters.keyword" placeholder="搜索用户名/姓名/手机号" clearable style="width: 220px"
              @keyup.enter="loadList" @clear="loadList" />
            <el-select v-model="filters.status" placeholder="账号状态" clearable style="width: 120px" @change="loadList">
              <el-option label="正常" :value="1" />
              <el-option label="禁用" :value="0" />
              <el-option label="未激活" :value="2" />
            </el-select>
            <el-button type="primary" @click="loadList">查询</el-button>
            <el-button type="success" v-permission="'sys:user:add'" @click="openEdit(null)">新增用户</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="55" />
        <el-table-column prop="username" label="登录账号" width="100" />
        <el-table-column prop="realName" label="姓名" width="90" />
        <el-table-column prop="dept" label="部门" width="100" show-overflow-tooltip />
        <el-table-column label="角色" min-width="100">
          <template #default="{ row }">
            <el-tag v-for="name in (row.roleNames || '').split('、').filter(Boolean)" :key="name" size="small"
              type="primary" class="role-tag">{{ name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="115" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ ['禁用', '正常', '未激活'][row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="160" />
        <el-table-column label="操作" width="245" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" v-permission="'sys:user:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" v-permission="'sys:user:reset'" @click="openResetPwd(row)">重置密码</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" v-permission="'sys:user:edit'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" v-permission="'sys:user:delete'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="mt12" background layout="total, prev, pager, next" :total="total"
        :page-size="filters.pageSize" v-model:current-page="filters.pageNum" @current-change="loadList" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="登录账号" required>
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号（创建后不可修改）" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="form.gender" style="width: 100%">
                <el-option label="未选择" :value="0" />
                <el-option label="男" :value="1" />
                <el-option label="女" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门">
              <el-input v-model="form.dept" placeholder="部门" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-input v-model="form.position" placeholder="岗位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="直属上级">
              <el-input v-model="form.leader" placeholder="直属上级" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="分配角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%" placeholder="选择一个或多个角色">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert v-if="!form.id" type="info" :closable="false"
          title="新增用户默认初始密码为 123456，可在创建后重置。" />
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdVisible" :title="'重置密码 - ' + (current && current.username)" width="420px">
      <el-form label-width="90px">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="留空则重置为默认密码 123456" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPage, addUser, updateUser, deleteUser, resetUserPassword, changeUserStatus, getRoleList } from '../api/sys'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const roles = ref([])

const filters = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: null })

const loadList = async () => {
  loading.value = true
  try {
    const data = await getUserPage(filters)
    list.value = data.records
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

// 新增/编辑
const editVisible = ref(false)
const form = ref({})
const emptyForm = () => ({
  id: null, username: '', realName: '', phone: '', email: '',
  gender: 0, position: '', dept: '', leader: '', roleIds: [], status: 1
})

const openEdit = row => {
  form.value = row ? { ...emptyForm(), ...row, roleIds: [...(row.roleIds || [])] } : emptyForm()
  editVisible.value = true
}

const submitEdit = async () => {
  if (!form.value.username || !form.value.realName) {
    ElMessage.warning('请填写登录账号和姓名')
    return
  }
  submitting.value = true
  try {
    if (form.value.id) {
      await updateUser(form.value.id, form.value)
    } else {
      await addUser(form.value)
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

// 重置密码
const pwdVisible = ref(false)
const current = ref(null)
const newPassword = ref('')

const openResetPwd = row => {
  current.value = row
  newPassword.value = ''
  pwdVisible.value = true
}

const submitResetPwd = async () => {
  submitting.value = true
  try {
    await resetUserPassword(current.value.id, newPassword.value)
    ElMessage.success('密码已重置' + (newPassword.value ? '' : '（默认 123456）'))
    pwdVisible.value = false
  } finally {
    submitting.value = false
  }
}

// 启用/禁用
const toggleStatus = async row => {
  const next = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确定${next === 0 ? '禁用' : '启用'}账号「${row.username}」吗？`, '提示', { type: 'warning' })
  await changeUserStatus(row.id, next)
  ElMessage.success('操作成功')
  loadList()
}

// 删除
const remove = async row => {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(async () => {
  loadList()
  roles.value = await getRoleList()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: nowrap; }
.mt12 { margin-top: 12px; }
.role-tag { margin: 2px 4px 2px 0; }
</style>
