<template>
  <div class="project-manage">
    <el-card shadow="never">
      <template #header>
        <div class="bar">
          <span>横向项目管理(点行展开到账明细管理)</span>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" row-key="projectId" @expand-change="onExpand">
        <!-- 行展开:到账明细管理 -->
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-box" v-loading="fundingLoading[row.projectId]">
              <div class="fund-bar">
                <span>到账明细(录入即通知教师;累计实时求和不回写)</span>
                <span class="total">累计到账:<b>{{ fmtMoney(fundTotal[row.projectId]) }}</b> 元</span>
                <el-button size="small" type="primary" @click="openAdd(row)">录入到账</el-button>
              </div>
              <el-table :data="fundRows[row.projectId] || []" size="small" border>
                <el-table-column label="到账日期" width="110">
                  <template #default="{ row: r }">{{ String(r.receivedDate || '').slice(0, 10) }}</template>
                </el-table-column>
                <el-table-column label="金额(元)" width="130" align="right">
                  <template #default="{ row: r }">{{ fmtMoney(r.amount) }}</template>
                </el-table-column>
                <el-table-column prop="payer" label="付款方" min-width="140" show-overflow-tooltip />
                <el-table-column prop="bankFlowNo" label="银行流水号" width="140" show-overflow-tooltip />
                <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
                <el-table-column prop="createBy" label="录入人" width="90" />
                <el-table-column label="操作" width="70">
                  <template #default="{ row: r }">
                    <el-button link type="danger" size="small" @click="removeFunding(row, r)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!(fundRows[row.projectId] || []).length" description="暂无到账记录" :image-size="60" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="approvalNo" label="编号" width="110">
          <template #default="{ row }">{{ row.approvalNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="项目名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="leaderName" label="负责人" width="90" />
        <el-table-column prop="deptName" label="院部" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="clientName" label="委托单位" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.clientName || '—' }}</template>
        </el-table-column>
        <el-table-column label="合同经费(元)" width="120" align="right">
          <template #default="{ row }">{{ fmtMoney(row.contractAmount) }}</template>
        </el-table-column>
        <el-table-column label="到账累计(元)" width="120" align="right">
          <template #default="{ row }">{{ fmtMoney(row.receivedTotal) }}</template>
        </el-table-column>
        <el-table-column label="执行状态" width="120">
          <template #default="{ row }">
            <el-tag :type="EXEC_TAG[row.execStatus] || 'info'" size="small">{{ row.execStatus }}</el-tag>
            <el-tooltip v-if="(row.pendingFlows || []).length" placement="top"
              :content="'在途:' + row.pendingFlows.map((f) => FLOW_LABEL[f.flowType]).join('、')">
              <el-tag size="small" type="warning" effect="plain" class="ml-4">
                {{ (row.pendingFlows || []).length }} 在途
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="登记日期" width="110">
          <template #default="{ row }">{{ String(row.hxRegisterDate || '').slice(0, 10) || '—' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无横向项目" :image-size="70" />
    </el-card>

    <!-- 录入到账 -->
    <el-dialog v-model="addVisible" :title="`录入到账-${addTarget?.name || ''}`" width="520px">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="110px">
        <el-form-item label="到账金额(元)" prop="amount">
          <el-input-number v-model="addForm.amount" :min="0.01" :precision="2" :controls="false"
            placeholder="金额,单位元" class="w-full" />
        </el-form-item>
        <el-form-item label="到账日期" prop="receivedDate">
          <el-date-picker v-model="addForm.receivedDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择日期" class="w-full" />
        </el-form-item>
        <el-form-item label="付款方">
          <el-input v-model="addForm.payer" :placeholder="addTarget?.clientName || '默认=委托单位,可改'" />
        </el-form-item>
        <el-form-item label="银行流水号">
          <el-input v-model="addForm.bankFlowNo" placeholder="选填" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveFunding">录入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<!--
  横向项目管理(spec §7.2 决策#21):九列表格(编号/名称/负责人/院部/委托单位/合同经费/
  到账累计/执行状态/登记日期)+在途流程单提示;行展开=到账明细管理(admin 增删,Task25 接口)。
-->
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addFunding, adminFunding, deleteFunding, projectList } from '../../api/hx'

const loading = ref(false)
const rows = ref([])
const fundRows = reactive({})      // projectId → records
const fundTotal = reactive({})     // projectId → total
const fundingLoading = reactive({})

const EXEC_TAG = { 登记中: 'warning', 在研: 'success', 变更中: 'warning', 已结题: 'info' }
const FLOW_LABEL = { register: '登记', change: '变更', acceptance: '验收' }

const fmtMoney = (v) => (v === null || v === undefined ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))

async function load() {
  loading.value = true
  try {
    rows.value = (await projectList()) || []
  } finally {
    loading.value = false
  }
}

/** 展开行懒加载到账明细(首展拉取,之后用缓存) */
async function loadFunding(row) {
  if (fundRows[row.projectId]) return
  fundingLoading[row.projectId] = true
  try {
    const d = (await adminFunding(row.projectId)) || {}
    fundRows[row.projectId] = d.records || []
    fundTotal[row.projectId] = d.total ?? 0
  } finally {
    fundingLoading[row.projectId] = false
  }
}

/** 展开事件(el-table expand-change) */
function onExpand(row, expanded) {
  if (expanded.includes(row)) loadFunding(row)
}

/* ---------- 录入/删除 ---------- */
const addVisible = ref(false)
const saving = ref(false)
const addTarget = ref(null)
const addFormRef = ref()
const addForm = reactive({ amount: null, receivedDate: '', payer: '', bankFlowNo: '', remark: '' })

const addRules = {
  amount: [{ required: true, message: '请输入到账金额', trigger: 'change' }],
  receivedDate: [{ required: true, message: '请选择到账日期', trigger: 'change' }]
}

function openAdd(row) {
  addTarget.value = row
  addForm.amount = null
  addForm.receivedDate = ''
  addForm.payer = ''
  addForm.bankFlowNo = ''
  addForm.remark = ''
  addVisible.value = true
}

async function saveFunding() {
  await addFormRef.value.validate()
  saving.value = true
  try {
    await addFunding({
      projectId: addTarget.value.projectId,
      amount: addForm.amount,
      receivedDate: addForm.receivedDate,
      payer: addForm.payer || undefined,
      bankFlowNo: addForm.bankFlowNo || undefined,
      remark: addForm.remark || undefined
    })
    ElMessage.success('已录入并通知教师')
    addVisible.value = false
    await reloadFund(addTarget.value)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function removeFunding(row, r) {
  try {
    await ElMessageBox.confirm(
      `删除到账 ${fmtMoney(r.amount)} 元?累计将自动回落并通知教师核销。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteFunding(r.id)
    ElMessage.success('已删除')
    await reloadFund(row)
  } catch (e) { /* 拦截器已提示 */ }
}

/** 重拉单项目到账+全表(累计列联动) */
async function reloadFund(row) {
  fundingLoading[row.projectId] = true
  try {
    const d = (await adminFunding(row.projectId)) || {}
    fundRows[row.projectId] = d.records || []
    fundTotal[row.projectId] = d.total ?? 0
    await load()
  } finally {
    fundingLoading[row.projectId] = false
  }
}

onMounted(load)
</script>

<script>
export default { name: 'ProjectManage' }
</script>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.expand-box {
  padding: 8px 16px 14px;
}

.fund-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}

.fund-bar .total {
  flex: 1;
  text-align: right;
  color: #303133;
}

.ml-4 {
  margin-left: 4px;
}
</style>
