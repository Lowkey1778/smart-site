<template>
  <div>
    <el-alert type="info" :closable="false" class="mb16"
      title="操作日志审计：登录、权限变更、告警处置、喷淋控制等关键操作自动留痕（T-36）" />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><Document /></el-icon> 操作日志</span>
          <div>
            <el-select v-model="filters.module" placeholder="模块" clearable style="width: 140px" @change="loadList">
              <el-option label="认证" value="认证" />
              <el-option label="用户管理" value="用户管理" />
              <el-option label="角色管理" value="角色管理" />
              <el-option label="告警处置" value="告警处置" />
              <el-option label="喷淋控制" value="喷淋控制" />
            </el-select>
            <el-input v-model="filters.keyword" placeholder="搜索用户/内容" clearable style="width: 180px; margin-left: 8px"
              @keyup.enter="loadList" @clear="loadList" />
            <el-button size="small" class="ml8" @click="loadList">查询</el-button>
            <el-button size="small" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" size="small" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="createTime" label="操作时间" width="170" />
        <el-table-column prop="username" label="操作人" width="120" align="center" />
        <el-table-column prop="module" label="模块" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="moduleTag(row.module)">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="120" align="center" />
        <el-table-column prop="content" label="详情" min-width="260" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" align="center" />
      </el-table>

      <el-pagination class="mt12" background layout="total, prev, pager, next" :total="total"
        :page-size="filters.pageSize" v-model:current-page="filters.pageNum" @current-change="loadList" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const filters = ref({ pageNum: 1, pageSize: 10, module: null, keyword: null })

const moduleTag = m => ({ 认证: 'success', 用户管理: '', 角色管理: 'warning', 告警处置: 'danger', 喷淋控制: 'info' }[m] || 'info')

const loadList = async () => {
  loading.value = true
  try {
    const data = await request.get('/log/list', {
      params: {
        pageNum: filters.value.pageNum,
        pageSize: filters.value.pageSize,
        module: filters.value.module || undefined,
        keyword: filters.value.keyword || undefined
      }
    })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.ml8 { margin-left: 8px; }
.mt12 { margin-top: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
