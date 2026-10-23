<template>
  <div class="reg-page">
    <el-alert v-if="rejected" type="warning" :closable="false" class="topbar"
      :title="'退回单:修改后请点击「保存并重交」。退回意见:' + (rejectOpinion || '见时间线')" />
    <el-alert v-else-if="flowStatus === 1 || flowStatus === 2" type="info" :closable="false" class="topbar"
      title="已提交:审批通过前仍可直接修改数据;打印请用右上「打印」,盖章扫描件与最新数据的差异请留意提示。" />

    <div class="btoolbar">
      <label class="hintswitch"><input type="checkbox" v-model="showHints">显示填写提示</label>
      <el-upload v-if="canEdit" :show-file-list="false" :http-request="doUploadContract"
        :accept="'.pdf,.png,.jpg,.jpeg'">
        <el-button size="small">{{ contractFile ? '重传合同扫描件' : '上传合同扫描件' }}</el-button>
      </el-upload>
      <el-button v-if="canEdit" size="small" :loading="saving" @click="save">保存草稿</el-button>
      <el-button v-if="canEdit" size="small" type="primary" @click="submit">{{ rejected ? '保存并重交' : '提交' }}</el-button>
      <el-button v-if="flowStatus >= 1" size="small" type="primary" plain @click="print">打印 / 导出 PDF</el-button>
    </div>

    <RegisterSheet ref="sheetRef" :model="form" :mode="canEdit ? 'edit' : 'print'"
      :show-hints="showHints" :meta="meta" @change="onChange" />
  </div>
</template>

<!--
  教师登记填单页(HTML转正):表即填单页(表格内直接填),替代原三步向导;
  审批前(0/1/2/4)可编辑,提交后打印按钮开放(新标签 PrintPage);
  退回单保存后走 resubmit。合同扫描件上传沿旧口径(先存草稿拿 flowId)。
-->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  flowDetail, getConfig, listDepts, listDict, resubmit, saveRegisterDraft, submitRegister,
  uploadContract
} from '../../api/hx'
import { useUserStore } from '../../stores/user'
import RegisterSheet from '../../components/hxsheet/RegisterSheet.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const showHints = ref(true)
const saving = ref(false)
const submitting = ref(false)
const flowStatus = ref(0)
const rejected = ref(false)
const rejectOpinion = ref('')
const contractFile = ref('')

const meta = reactive({
  approvalNo: '', leaderPhone: '', hxRegisterDate: '', mgmtRate: null,
  dictProjectType: [], dictClientNature: [], dictTitle: [], dictBudgetSubject: [], depts: []
})

const form = reactive({
  flowId: null,
  name: '',
  leaderName: '',
  deptId: null,
  hx: {
    projectType: '', clientName: '', clientNature: '', contractAmount: null,
    startDate: '', endDate: '', useSchoolAsset: 0, chargeMgmtFee: 0,
    leaderEmail: '', leaderPhone: '', projectSummary: '', progressPlan: ''
  },
  members: [{ memberType: '教师', employeeNo: '', name: '', unit: '', title: '', major: '', duty: '' }],
  budgets: []
})

const canEdit = computed(() => [0, 1, 2, 4].includes(flowStatus.value))

function collectForm() {
  const members = form.members
    .filter((m) => (m.name || '').trim())
    .map((m, i) => ({ ...m, name: m.name.trim(), sortNo: i + 1 }))
  const budgets = form.budgets
    .filter((b) => b.unrestricted === 1 || (b.amount !== null && b.amount !== undefined && b.amount !== ''))
    .map((b) => ({ subject: b.subject, amount: b.amount, unrestricted: b.unrestricted || 0 }))
  return {
    flowId: form.flowId,
    name: form.name,
    leaderName: form.leaderName,
    deptId: form.deptId,
    hx: { ...form.hx },
    members,
    budgets
  }
}

let changeTimer = null
function onChange() {
  clearTimeout(changeTimer)
  changeTimer = setTimeout(autoSave, 1500)
}
async function autoSave() {
  if (!canEdit.value || saving.value || submitting.value) return
  if (!form.name || !form.hx.projectType) return   // 关键必填未齐不自动存
  await doSave(false)
}

async function doSave(toast = true) {
  saving.value = true
  try {
    const fid = await saveRegisterDraft(collectForm())
    form.flowId = fid
    flowStatus.value = (await flowDetail(fid)).flow.status
    if (String(route.params.flowId || '') !== String(fid)) {
      router.replace(`/hx/register/${fid}`)
    }
    if (toast) ElMessage.success('草稿已保存')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

function save() { return doSave(true) }

async function submit() {
  const payload = collectForm()
  if (!payload.name || !payload.hx.projectType || !payload.hx.clientName || !payload.hx.clientNature
    || payload.hx.contractAmount === null || !payload.hx.startDate || !payload.hx.endDate
    || !payload.leaderName || !payload.deptId) {
    ElMessage.warning('请先填齐必填项(名称/负责人/院部/类型/委托单位/性质/经费/起止)')
    return
  }
  const total = payload.budgets.filter((b) => !b.unrestricted)
    .reduce((s, b) => s + (Number(b.amount) || 0), 0)
  if (Math.abs(total - Number(payload.hx.contractAmount)) > 0.005) {
    ElMessage.warning(`预算合计(${total.toFixed(2)})须等于合同经费(${Number(payload.hx.contractAmount).toFixed(2)})元`)
    return
  }
  /* 提交确认(HTML转正口径):审批前仍可改,但打印签章版必须为最终修改版 */
  const tip = rejected.value
    ? '重交后原盖章件作废,需重新盖章回扫。'
    : '提交后仍可修改,但打印版必须为最终修改版。'
  try {
    await ElMessageBox.confirm(tip + '请确认当前填报数据无误。', '确认提交',
      { confirmButtonText: '确认提交', cancelButtonText: '再检查一下', type: 'info' })
  } catch (e) {
    return   // 用户取消
  }
  submitting.value = true
  try {
    if (!form.flowId) {
      form.flowId = await saveRegisterDraft(payload)
    } else {
      await saveRegisterDraft(payload)
    }
    if (rejected.value) {
      await resubmit(form.flowId)
      ElMessage.success('已重交;请重新打印签章并回扫(原盖章件作废)')
    } else {
      await submitRegister(form.flowId)
      ElMessage.success('已提交;请在流程详情打印待签件(浏览器打印 A4/另存 PDF)')
    }
    router.replace(`/hx/flow/${form.flowId}`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function print() {
  window.open(`/print/register/${form.flowId}`, '_blank')
}

async function doUploadContract(opt) {
  try {
    if (!form.flowId) {
      form.flowId = await saveRegisterDraft(collectForm())
      if (String(route.params.flowId || '') !== String(form.flowId)) {
        router.replace(`/hx/register/${form.flowId}`)
      }
    }
    await uploadContract(form.flowId, opt.file)
    contractFile.value = opt.file.name
    ElMessage.success('合同扫描件已上传')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function loadDraft(flowId) {
  const d = await flowDetail(flowId)
  if (![0, 1, 2, 4].includes(d.flow.status)) {
    router.replace(`/hx/flow/${flowId}`)
    return
  }
  rejected.value = d.flow.status === 4
  if (rejected.value) {
    const rec = (d.records || []).filter((x) => ['reject', 'precheck_reject'].includes(x.action)).pop()
    rejectOpinion.value = rec ? rec.opinion || '' : ''
  }
  flowStatus.value = d.flow.status
  meta.approvalNo = d.project.approvalNo || ''
  meta.hxRegisterDate = d.hx.hxRegisterDate || ''
  form.flowId = d.flow.id
  contractFile.value = (d.attachments || []).some((a) => a.bizType === 'hx_apply') ? '合同扫描件' : ''
  form.name = d.project.name
  form.leaderName = d.project.leaderName
  form.deptId = d.project.deptId
  const hx = d.hx || {}
  Object.assign(form.hx, {
    projectType: hx.projectType || '', clientName: hx.clientName || '',
    clientNature: hx.clientNature || '', contractAmount: hx.contractAmount,
    startDate: hx.startDate ? String(hx.startDate).slice(0, 10) : '',
    endDate: hx.endDate ? String(hx.endDate).slice(0, 10) : '',
    useSchoolAsset: hx.useSchoolAsset ?? 0, chargeMgmtFee: hx.chargeMgmtFee ?? 0,
    leaderEmail: hx.leaderEmail || '',
    leaderPhone: hx.leaderPhone || (form.hx.leaderPhone || ''),   // 存量优先,否则保留带出的手机号
    projectSummary: hx.projectSummary || '',
    progressPlan: hx.progressPlan || ''
  })
  form.members = (d.members || []).map((m) => ({
    memberType: m.memberType || '教师', employeeNo: m.employeeNo || '', name: m.name || '',
    unit: m.unit || '', title: m.title || '', major: m.major || '', duty: m.duty || ''
  }))
  if (!form.members.length) {
    form.members = [{ memberType: '教师', employeeNo: '', name: '', unit: '', title: '', major: '', duty: '' }]
  }
  const bySubject = Object.fromEntries((d.budgets || []).map((b) => [b.subject, b]))
  form.budgets = meta.dictBudgetSubject.map((s) => ({
    subject: s, amount: bySubject[s] ? bySubject[s].amount : null,
    unrestricted: bySubject[s] ? bySubject[s].unrestricted || 0 : 0
  }))
}

onMounted(async () => {
  form.leaderName = userStore.userInfo?.realName || ''
  /* 联系电话默认带出本人手机号(/users/me),存量回填时以项目存值优先 */
  form.hx.leaderPhone = userStore.userInfo?.phone || ''
  getConfig('hx_mgmt_fee_rate').then((v) => { meta.mgmtRate = v == null ? null : Number(v) })
  const [ds, pt, cn, tt, bs] = await Promise.all([
    listDepts(), listDict('hx_project_type'), listDict('hx_client_nature'), listDict('title'),
    listDict('hx_budget_subject')
  ])
  meta.depts = ds
  meta.dictProjectType = pt.map((x) => x.label)
  meta.dictClientNature = cn.map((x) => x.label)
  meta.dictTitle = tt.map((x) => x.label)
  meta.dictBudgetSubject = bs.map((x) => x.label)
  form.budgets = meta.dictBudgetSubject.map((s) => ({ subject: s, amount: null, unrestricted: 0 }))
  if (route.params.flowId) {
    await loadDraft(Number(route.params.flowId))
  }
})
</script>

<style scoped>
.reg-page {
  min-height: calc(100vh - 120px);
}
.topbar {
  margin-bottom: 8px;
}
</style>
