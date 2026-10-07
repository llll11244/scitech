<template>
  <div class="funding-view">
    <el-card shadow="never">
      <template #header>
        <div class="bar">
          <span>到账明细(只读)</span>
          <el-select v-model="projectId" filterable placeholder="选择项目" class="w-360" @change="load">
            <el-option v-for="p in projects" :key="p.projectId" :label="p.name" :value="p.projectId" />
          </el-select>
        </div>
      </template>

      <template v-if="projectId">
        <el-alert type="info" :closable="false" class="mb"
          title="到账由科技处按计财处/银行流水录入,教师只读;录入与核销均自动发通知。" />
        <div class="total-bar">
          累计到账:<b>{{ fmtMoney(total) }}</b> 元
        </div>
        <el-table :data="records" v-loading="loading" size="default" border>
          <el-table-column label="到账日期" width="120">
            <template #default="{ row }">{{ String(row.receivedDate || '').slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="金额(元)" width="140" align="right">
            <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="payer" label="付款方" min-width="160" show-overflow-tooltip />
          <el-table-column prop="bankFlowNo" label="银行流水号" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.bankFlowNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column prop="createBy" label="录入人" width="100">
            <template #default="{ row }">{{ row.createBy || '—' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !records.length" description="暂无到账记录" :image-size="70" />
      </template>
      <el-empty v-else description="请选择要查看的项目" :image-size="70" />
    </el-card>
  </div>
</template>

<!--
  教师到账只读(spec §7.1 项目详情-到账):本人项目切换+明细+累计(total 可能 null 按 0);
  数据源 GET /hx/{projectId}/funding(Service 层 apply_user_id 强制)。
-->
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { myProjects, teacherFunding } from '../../api/hx'

const route = useRoute()
const loading = ref(false)
const projects = ref([])
const projectId = ref(null)
const records = ref([])
const total = ref(0)

const fmtMoney = (v) => (v === null || v === undefined ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const d = (await teacherFunding(projectId.value)) || {}
    records.value = d.records || []
    total.value = d.total ?? 0   // 无记录时后端回 null,按 0 展示
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const rows = (await myProjects()) || []
  // 到账视角=已入台账项目优先;未确认项目也可查(科技处可能先录款)
  projects.value = rows
  const qpid = Number(route.query.projectId)
  if (qpid && rows.some((p) => p.projectId === qpid)) {
    projectId.value = qpid
  } else if (rows.length) {
    projectId.value = rows[0].projectId
  }
  await load()
})
</script>

<script>
export default { name: 'FundingView' }
</script>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.w-360 {
  width: 360px;
}

.mb {
  margin-bottom: 12px;
}

.total-bar {
  margin-bottom: 10px;
  font-size: 15px;
  color: #303133;
}

.sub {
  margin-left: 10px;
  font-size: 13px;
  color: #909399;
}
</style>
