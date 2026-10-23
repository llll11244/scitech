<template>
  <div class="audit-list">
    <el-card shadow="never">
      <template #header>
        <div class="bar">
          <span>横向审批</span>
          <div class="filters">
            <el-select v-model="flowType" placeholder="全部类型" clearable size="default" class="w-140"
              @change="load">
              <el-option label="登记表" value="register" />
              <el-option label="变更表" value="change" />
              <el-option label="验收单" value="acceptance" />
            </el-select>
            <el-select v-model="status" placeholder="全部状态" clearable size="default" class="w-140"
              @change="load">
              <el-option v-for="(v, k) in STATUS" :key="k" :label="v.label" :value="Number(k)" />
            </el-select>
          </div>
        </div>
      </template>

      <!-- 关键字:名称/负责人/院部,前端实时过滤(类型/状态走后端参数) -->
      <div class="qbar">
        <el-input v-model="qKeyword" placeholder="项目名称 / 负责人 / 院部" clearable class="w-240"
          prefix-icon="Search" />
        <el-button @click="qKeyword = ''">重置</el-button>
      </div>

      <el-table :data="filtered" v-loading="loading" @row-click="open">
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag :type="TYPE_TAG[row.flowType]?.type || 'info'" effect="plain">
              {{ TYPE_TAG[row.flowType]?.label || row.flowType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="项目名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="leaderName" label="负责人" width="100" />
        <el-table-column prop="deptName" label="院部" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="STATUS[row.status]?.type || 'info'" size="small">
              {{ STATUS[row.status]?.label || row.status }}
            </el-tag>
            <!-- status=1=盖章前,可预检退回(决策#18) -->
            <el-tag v-if="row.status === 1" size="small" type="warning" effect="plain" class="ml-4">
              可预检
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更类型" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.flowType === 'change' ? (row.changeType || '—') : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.submitTime) }}</template>
        </el-table-column>
        <el-table-column label="回扫时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.scanTime) || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="open(row)">
              {{ row.status === 1 || row.status === 2 ? '审批' : '查看' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filtered.length"
        :description="qKeyword ? '没有符合条件的流程单' : '暂无流程单'" :image-size="70" />
    </el-card>
  </div>
</template>

<!--
  科技处审批列表(spec §7.2):flowType 标签+状态筛选,status=1 带「可预检」标记;
  点行进审批详情。量小不分页(与后端口径一致)。
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auditList } from '../../api/hx'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const flowType = ref(null)
const status = ref(null)

/* 关键字:名称/负责人/院部,前端实时过滤 */
const qKeyword = ref('')
const filtered = computed(() => {
  const kw = qKeyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((row) => [row.name, row.leaderName, row.deptName]
    .some((v) => (v || '').toLowerCase().includes(kw)))
})

const STATUS = {
  0: { label: '草稿', type: 'info' },
  1: { label: '已提交待回扫', type: 'warning' },
  2: { label: '已回扫待审核', type: 'primary' },
  3: { label: '已通过', type: 'success' },
  4: { label: '已退回', type: 'danger' }
}
const TYPE_TAG = {
  register: { label: '登记表', type: 'primary' },
  change: { label: '变更表', type: 'warning' },
  acceptance: { label: '验收单', type: 'success' }
}

const fmtTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

async function load() {
  loading.value = true
  try {
    const params = {}
    if (flowType.value) params.flowType = flowType.value
    if (status.value !== null && status.value !== undefined && status.value !== '') params.status = status.value
    rows.value = (await auditList(params)) || []
  } finally {
    loading.value = false
  }
}

function open(row) {
  router.push(`/admin/audit/${row.flowId}`)
}

onMounted(load)
</script>

<script>
export default { name: 'AuditList' }
</script>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.qbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.w-240 {
  width: 240px;
}
.filters {
  display: flex;
  gap: 10px;
}

.w-140 {
  width: 140px;
}

.ml-4 {
  margin-left: 4px;
}

.el-table >>> .el-table__row {
  cursor: pointer;
}
</style>
