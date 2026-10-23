<template>
  <div class="my-projects">
    <el-card shadow="never">
      <template #header>
        <div class="bar">
          <span>我的横向项目</span>
          <el-button type="primary" @click="$router.push('/hx/register')">新建登记</el-button>
        </div>
      </template>

      <!-- 查询栏:关键字(名称/编号/负责人)+状态,前端实时过滤 -->
      <div class="qbar">
        <el-input v-model="qKeyword" placeholder="名称 / 编号 / 负责人" clearable class="w-220"
          prefix-icon="Search" />
        <el-select v-model="qState" placeholder="全部状态" clearable class="w-150">
          <el-option v-for="s in STATE_OPTIONS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button @click="qKeyword = ''; qState = ''">重置</el-button>
      </div>

      <el-empty v-if="!loading && !filtered.length"
        :description="qKeyword || qState ? '没有符合条件的项目' : '暂无项目,点击右上角「新建登记」发起第一项横向项目'" />

      <el-table v-else :data="paged" v-loading="loading">
        <el-table-column label="项目名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="leaderName" label="负责人" width="110" />
        <el-table-column prop="approvalNo" label="编号" width="130">
          <template #default="{ row }">{{ row.approvalNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="stateOf(row).type">{{ stateOf(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近动态" width="170">
          <template #default="{ row }">{{ lastActive(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <!-- 登记阶段 -->
            <template v-if="row.auditStatus < 2">
              <template v-if="[0, 1, 2, 4].includes(registerFlow(row)?.status)">
                <el-button link type="primary" @click="$router.push(`/hx/register/${registerFlow(row).flowId}`)">{{ registerFlow(row).status === 0 ? '继续填报' : '修改填报' }}</el-button>
                <el-button v-if="registerFlow(row).status === 0" link type="danger" @click="removeDraft(registerFlow(row).flowId)">删除草稿</el-button>
              </template>
              <el-button v-else link type="primary" @click="$router.push(`/hx/flow/${registerFlow(row)?.flowId}`)">流程详情</el-button>
            </template>
            <!-- 立项后:变更/验收入口 -->
            <template v-else>
              <el-button v-if="inflightChange(row)" link type="primary"
                @click="$router.push(`/hx/change/${inflightChange(row).flowId}`)">{{ inflightChange(row).status === 0 ? '继续变更单' : '修改变更单' }}</el-button>
              <el-button v-else-if="inflightChange(row)" link type="primary"
                @click="$router.push(`/hx/flow/${inflightChange(row).flowId}`)">变更单详情</el-button>
              <el-button v-else link type="primary" @click="$router.push(`/hx/change?projectId=${row.projectId}`)">发起变更</el-button>

              <el-button v-if="acceptanceFlow(row) && [0, 1, 2, 4].includes(acceptanceFlow(row).status)"
                link type="primary"
                @click="$router.push(`/hx/acceptance/${acceptanceFlow(row).flowId}`)">{{ acceptanceFlow(row).status === 0 ? '继续验收单' : '修改验收单' }}</el-button>
              <el-button v-else-if="acceptanceFlow(row)" link type="primary"
                @click="$router.push(`/hx/flow/${acceptanceFlow(row).flowId}`)">验收单详情</el-button>
              <el-button v-else-if="row.status !== '结题'" link type="primary" @click="$router.push(`/hx/acceptance?projectId=${row.projectId}`)">发起验收</el-button>
            </template>
            <el-button link type="primary" @click="$router.push(`/hx/funding?projectId=${row.projectId}`)">到账</el-button>
            <el-button v-if="row.auditStatus >= 2" link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页:默认 20/页,可调 10/30/50;过滤/删行后页码自动回正 -->
      <el-pagination v-if="filtered.length" v-model:current-page="currentPage"
        v-model:page-size="pageSize" :page-sizes="[10, 20, 30, 50]" :total="filtered.length"
        layout="total, sizes, prev, pager, next" background class="pager" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myProjects, deleteDraft } from '../../api/hx'

const router = useRouter()
const loading = ref(false)
const rows = ref([])

/* 查询栏:关键字(名称/编号/负责人)+状态,前端实时过滤(量小即时反馈) */
const qKeyword = ref('')
const qState = ref('')
const STATE_OPTIONS = ['被退回', '登记草稿', '登记中', '变更中', '验收中', '在研', '已结题']
const filtered = computed(() => {
  const kw = qKeyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (kw && ![row.name, row.approvalNo, row.leaderName]
      .some((v) => (v || '').toLowerCase().includes(kw))) {
      return false
    }
    if (qState.value) {
      const label = stateOf(row).label
      const hit = qState.value === '被退回' ? label.includes('被退回') : label === qState.value
      if (!hit) return false
    }
    return true
  })
})

/* 分页:默认 20/页可调;过滤条件变化回第 1 页,行数缩减自动钳制越界页码 */
const currentPage = ref(1)
const pageSize = ref(20)
const paged = computed(() =>
  filtered.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
watch([qKeyword, qState], () => { currentPage.value = 1 })
watch(() => filtered.value.length, (n) => {
  const maxPage = Math.max(1, Math.ceil(n / pageSize.value))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})

const registerFlow = (row) => row.flows?.find((f) => f.flowType === 'register')
/** 在途变更单(0/1/2/4;3 通过后可再发新单) */
const inflightChange = (row) =>
  row.flows?.find((f) => f.flowType === 'change' && [0, 1, 2, 4].includes(f.status))
/** 验收单每项目至多一张(任何状态) */
const acceptanceFlow = (row) => row.flows?.find((f) => f.flowType === 'acceptance')

/** 行状态 tag:已结题 > 被退回(任一在途单 status=4,醒目提示修改重交) > 登记中(audit<2) > 变更中 > 验收中 > 在研 */
const FLOW_NAMES = { register: '登记', change: '变更', acceptance: '验收' }
function stateOf(row) {
  if (row.status === '结题') return { label: '已结题', type: 'info' }
  const rejected = row.flows?.find((f) => f.status === 4)
  if (rejected) return { label: `${FLOW_NAMES[rejected.flowType] || '流程'}单被退回`, type: 'danger' }
  if (row.auditStatus < 2) {
    return { label: row.auditStatus === 0 ? '登记草稿' : '登记中', type: 'warning' }
  }
  if (inflightChange(row)) return { label: '变更中', type: 'warning' }
  if (acceptanceFlow(row) && acceptanceFlow(row).status !== 3) return { label: '验收中', type: 'warning' }
  return { label: '在研', type: 'success' }
}

/** 最近动态 = 项目创建与各流程单提交/回扫时间取最大 */
function lastActive(row) {
  const ts = [row.createTime]
  row.flows?.forEach((f) => ts.push(f.submitTime, f.scanTime))
  const max = ts.filter(Boolean).sort().pop() || ''
  return max.replace('T', ' ').slice(0, 16)
}

/** 名称点击入口:登记草稿回填报表单,其余打开最关心的一张流程单详情 */
function openDetail(row) {
  const reg = registerFlow(row)
  if (row.auditStatus < 2 && reg && [0, 1, 2, 4].includes(reg.status)) {
    router.push(`/hx/register/${reg.flowId}`)
    return
  }
  const prefer =
    row.auditStatus < 2
      ? reg
      : acceptanceFlow(row) || inflightChange(row) || reg
  if (prefer) router.push(`/hx/flow/${prefer.flowId}`)
}

async function removeDraft(flowId) {
  try {
    await ElMessageBox.confirm('删除后该项目草稿及已填成员/预算一并清除,确定删除?', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteDraft(flowId)
    ElMessage.success('草稿已删除')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function load() {
  loading.value = true
  try {
    rows.value = (await myProjects()) || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<script>
export default { name: 'MyProjects' }
</script>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
/* 查询栏:一行排布,间距 8px */
.qbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.w-220 {
  width: 220px;
}
.w-150 {
  width: 150px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
