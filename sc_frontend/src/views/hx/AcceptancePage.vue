<template>
  <div class="acc-page">
    <el-alert v-if="rejected" type="warning" :closable="false" class="topbar"
      :title="'退回单:修改后请点击「保存并重交」。退回意见:' + (rejectOpinion || '见时间线')" />
    <el-alert v-else-if="receivedTotal === null || receivedTotal <= 0" type="warning" :closable="false"
      class="topbar" title="暂无到账记录:验收需按到账经费核算成员分配,请联系科技处录入到账后再提交。" />

    <div class="btoolbar">
      <label class="hintswitch"><input type="checkbox" v-model="showHints">显示填写提示</label>
      <template v-if="canEdit">
        <a class="tbtn" :style="saving ? 'opacity:.5' : ''" @click="save">{{ saving ? '保存中…' : '保存草稿' }}</a>
        <a class="tbtn" @click="submit">{{ rejected ? '保存并重交' : '提交' }}</a>
      </template>
      <a v-if="flowStatus >= 1" @click="print('hx_acceptance')">打印验收登记表</a>
      <a v-if="flowStatus >= 1" @click="print('hx_report')">打印验收报告</a>
    </div>

    <div v-if="mode === 'edit' && !form.flowId" class="pick">
      <span>选择已确认的项目:</span>
      <el-select v-model="form.projectId" filterable placeholder="仅列出已确认且未验收的项目"
        style="width: 360px" @change="pickProject">
        <el-option v-for="p in projects" :key="p.projectId" :value="p.projectId" :label="p.name" />
      </el-select>
    </div>

    <template v-if="form.flowId || meta.orig.name">
      <el-tabs v-model="activeTab" class="sheet-tabs">
        <el-tab-pane label="验收登记表" name="hx_acceptance">
          <AcceptanceSheet :model="form" :mode="mode" :show-hints="showHints" :meta="meta" @change="onChange" />
        </el-tab-pane>
        <el-tab-pane label="验收报告" name="hx_report">
          <ReportSheet :model="form" :mode="mode" :show-hints="showHints" :meta="meta" @change="onChange" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<!--
  教师验收填单页(HTML转正):验收登记表+验收报告同页双 tab,同一份数据;
  到账=系统带出只读(成员分配核算基准);成员自登记回显(补比例/金额),学生可增删;
  提交硬校验(比例=100%/金额=到账/使用≤到账)前后端同规则。
-->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flowDetail, listDict, myProjects, resubmit, saveAcceptanceDraft, submitAcceptance } from '../../api/hx'
import AcceptanceSheet from '../../components/hxsheet/AcceptanceSheet.vue'
import ReportSheet from '../../components/hxsheet/ReportSheet.vue'
import { toWan } from '../../utils/rmb'

const route = useRoute()
const router = useRouter()

const showHints = ref(true)
const saving = ref(false)
const submitting = ref(false)
const flowStatus = ref(0)
const rejected = ref(false)
const rejectOpinion = ref('')
const projects = ref([])
const activeTab = ref('hx_acceptance')

const meta = reactive({
  orig: {}, receivedTotal: null, verifiedAmountWan: '', verifierName: '',
  dictOutcomeForm: [], dictProjectType: [], dictClientNature: []
})

const form = reactive({
  flowId: null, projectId: null,
  usedAmount: null, finishDate: '', isOnTime: null,
  projectType: '', clientNature: '', outcomeForms: [], acceptSummary: '', reportContent: '',
  members: [], students: []
})

const mode = computed(() => (canEdit.value ? 'edit' : 'print'))
const canEdit = computed(() => [0, 1, 2, 4].includes(flowStatus.value))

function collectForm() {
  const members = form.members
    .filter((m) => (m.name || '').trim())
    .map((m, i) => ({ ...m, name: m.name.trim(), sortNo: i + 1 }))
  const students = form.students
    .filter((s) => (s.name || '').trim())
    .map((s, i) => ({ ...s, memberType: '学生', name: s.name.trim(), sortNo: members.length + i + 1 }))
  return {
    flowId: form.flowId,
    projectId: form.projectId,
    usedAmount: form.usedAmount,
    finishDate: form.finishDate || null,
    isOnTime: form.isOnTime,
    outcomeForm: form.outcomeForms.join(','),
    acceptSummary: form.acceptSummary,
    reportContent: form.reportContent,
    members: [...members, ...students]
  }
}

let changeTimer = null
function onChange() {
  clearTimeout(changeTimer)
  changeTimer = setTimeout(() => canEdit.value && form.projectId && doSave(false), 1500)
}

async function doSave(toast = true) {
  if (form.usedAmount === null && !form.acceptSummary) return
  saving.value = true
  try {
    const fid = await saveAcceptanceDraft(collectForm())
    form.flowId = fid
    flowStatus.value = (await flowDetail(fid)).flow.status
    if (String(route.params.flowId || '') !== String(fid)) {
      router.replace(`/hx/acceptance/${fid}`)
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
  const total = Number(meta.receivedTotal)
  if (!total || total <= 0) {
    ElMessage.warning('暂无到账记录,请联系科技处录入到账后再提交验收')
    return
  }
  if (form.usedAmount === null || form.isOnTime === null || !form.outcomeForms.length
    || !form.acceptSummary || !form.reportContent) {
    ElMessage.warning('使用经费/是否按期/成果形式/完成总结/报告正文必填')
    return
  }
  if (Number(form.usedAmount) > total) {
    ElMessage.warning(`使用经费不得超过到账经费(${total}元)`)
    return
  }
  const named = form.members.filter((m) => (m.name || '').trim())
  const ratioSum = named.reduce((s, m) => s + (Number(m.ratio) || 0), 0)
  const amountSum = named.reduce((s, m) => s + (Number(m.amount) || 0), 0)
  if (Math.abs(ratioSum - 100) > 0.005) {
    ElMessage.warning(`成员比例合计(${ratioSum.toFixed(2)}%)须等于 100%`)
    return
  }
  if (Math.abs(amountSum - total) > 0.005) {
    ElMessage.warning(`成员金额合计(${amountSum.toFixed(2)})须等于到账经费(${total})元`)
    return
  }
  /* 提交确认(HTML转正口径) */
  const tip = rejected.value
    ? '重交后原盖章件作废,需重新盖章回扫。'
    : '提交后仍可修改,但打印版必须为最终修改版。'
  try {
    await ElMessageBox.confirm(tip + '请确认当前填报数据无误。', '确认提交',
      { confirmButtonText: '确认提交', cancelButtonText: '再检查一下', type: 'info' })
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await doSave(false)
    if (rejected.value) {
      await resubmit(form.flowId)
      ElMessage.success('已重交;请重新打印签章并回扫(原盖章件作废)')
    } else {
      await submitAcceptance(form.flowId)
      ElMessage.success('已提交;请在流程详情分别打印验收登记表与验收报告')
    }
    router.replace(`/hx/flow/${form.flowId}`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function print(tab) {
  window.open(`/print/acceptance/${form.flowId}?doc=${tab}`, '_blank')
}

/* 实态与登记成员回显 */
async function fillFromProject(pid, regFlowId) {
  const d = await flowDetail(regFlowId)
  meta.orig = {
    approvalNo: d.project.approvalNo || '', name: d.project.name,
    leaderName: d.project.leaderName,
    clientName: d.hx.clientName, clientNature: d.hx.clientNature,
    startDate: d.hx.startDate ? String(d.hx.startDate).slice(0, 10) : '',
    endDate: d.hx.endDate ? String(d.hx.endDate).slice(0, 10) : '',
    deptName: d.project.deptName || ''
  }
  form.projectType = d.hx.projectType || ''
  form.clientNature = d.hx.clientNature || ''
  const d2 = await flowDetail(regFlowId)
  form.members = (d2.members || []).filter((m) => m.memberType !== '学生').map((m) => ({
    memberType: m.memberType || '教师', employeeNo: m.employeeNo || '', name: m.name || '',
    unit: m.unit || '', title: m.title || '', major: m.major || '', duty: m.duty || '',
    ratio: m.ratio ?? null, amount: m.amount ?? null
  }))
  form.students = (d2.members || []).filter((m) => m.memberType === '学生').map((m) => ({
    name: m.name || '', studentNo: m.studentNo || '', major: m.major || '', mentor: m.mentor || '',
    duty: m.duty || ''
  }))
  const acc = await myProjects()
  const row = (acc || []).find((x) => x.projectId === pid)
  meta.receivedTotal = row ? row.receivedTotal ?? null : null
}

async function pickProject(pid) {
  const p = projects.value.find((x) => x.projectId === pid)
  if (!p) return
  await fillFromProject(pid, p.flowId)
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
  meta.receivedTotal = d.receivedTotal ?? null
  meta.verifiedAmountWan = d.hx.verifiedAmount ? toWan(d.hx.verifiedAmount) : ''
  const verify = (d.records || []).filter((x) => x.action === 'verify').pop()
  meta.verifierName = verify ? verify.operatorName || '' : ''
  meta.orig = {
    approvalNo: d.project.approvalNo || '', name: d.project.name,
    leaderName: d.project.leaderName,
    clientName: d.hx.clientName, clientNature: d.hx.clientNature,
    startDate: d.hx.startDate ? String(d.hx.startDate).slice(0, 10) : '',
    endDate: d.hx.endDate ? String(d.hx.endDate).slice(0, 10) : '',
    deptName: d.project.deptName || ''
  }
  form.flowId = d.flow.id
  form.projectId = d.flow.projectId
  form.usedAmount = d.flow.usedAmount
  form.finishDate = d.flow.finishDate ? String(d.flow.finishDate).slice(0, 10) : ''
  form.isOnTime = d.flow.isOnTime
  form.outcomeForms = (d.flow.outcomeForm || '').split(',').filter(Boolean)
  form.acceptSummary = d.flow.acceptSummary || ''
  form.reportContent = d.flow.reportContent || ''
  form.projectType = d.hx.projectType || ''
  form.clientNature = d.hx.clientNature || ''
  form.members = (d.members || []).filter((m) => m.memberType !== '学生').map((m) => ({
    memberType: m.memberType || '教师', employeeNo: m.employeeNo || '', name: m.name || '',
    unit: m.unit || '', title: m.title || '', major: m.major || '', duty: m.duty || '',
    ratio: m.ratio ?? null, amount: m.amount ?? null
  }))
  form.students = (d.members || []).filter((m) => m.memberType === '学生').map((m) => ({
    name: m.name || '', studentNo: m.studentNo || '', major: m.major || '', mentor: m.mentor || '',
    duty: m.duty || ''
  }))
}

onMounted(async () => {
  const [of, pt, cn, my] = await Promise.all([
    listDict('hx_outcome_form'), listDict('hx_project_type'), listDict('hx_client_nature'), myProjects()
  ])
  meta.dictOutcomeForm = of.map((x) => x.label)
  meta.dictProjectType = pt.map((x) => x.label)
  meta.dictClientNature = cn.map((x) => x.label)
  projects.value = (my || []).filter((p) => p.auditStatus === 2)
  if (route.params.flowId) {
    await loadDraft(Number(route.params.flowId))
  } else if (route.query.projectId) {
    /* 「发起验收」入口:预选项目并回显登记成员 */
    const pid = Number(route.query.projectId)
    const p = projects.value.find((x) => x.projectId === pid)
    if (p) {
      form.projectId = pid
      await fillFromProject(pid, p.flowId)
    }
  }
})
</script>

<style scoped>
.topbar { margin-bottom: 8px; }
.pick {
  margin: 6px 4px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
</style>
