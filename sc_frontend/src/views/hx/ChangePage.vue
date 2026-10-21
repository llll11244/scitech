<template>
  <div class="chg-page">
    <el-alert v-if="rejected" type="warning" :closable="false" class="topbar"
      :title="'退回单:修改后请点击「保存并重交」。退回意见:' + (rejectOpinion || '见时间线')" />

    <div class="btoolbar">
      <label class="hintswitch"><input type="checkbox" v-model="showHints">显示填写提示</label>
      <template v-if="canEdit">
        <a class="tbtn" :style="saving ? 'opacity:.5' : ''" @click="save">{{ saving ? '保存中…' : '保存草稿' }}</a>
        <a class="tbtn" @click="submit">{{ rejected ? '保存并重交' : '提交' }}</a>
      </template>
      <a v-if="flowStatus >= 1" @click="print">打印 / 导出 PDF</a>
    </div>

    <!-- 项目选择(屏显操作条;选定后 A 区实态只读) -->
    <div v-if="mode === 'edit' && !form.flowId" class="pick">
      <span>选择已确认的项目:</span>
      <el-select v-model="form.projectId" filterable placeholder="仅列出已确认(立项)项目"
        style="width: 360px" @change="pickProject">
        <el-option v-for="p in projects" :key="p.projectId" :value="p.projectId" :label="p.name" />
      </el-select>
    </div>

    <ChangeSheet v-if="meta.orig.name || form.flowId" :model="form" :mode="mode"
      :show-hints="showHints" :meta="meta" @change="onChange" />
  </div>
</template>

<!--
  教师变更填单页(HTML转正):先选已确认项目(在途变更唯一后端守卫)→表内填报;
  A 区=项目实态带出只读;审批前可改;提交后打印开放。
-->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flowDetail, listDict, myProjects, resubmit, saveChangeDraft, submitChange } from '../../api/hx'
import ChangeSheet from '../../components/hxsheet/ChangeSheet.vue'

const route = useRoute()
const router = useRouter()

const showHints = ref(true)
const saving = ref(false)
const submitting = ref(false)
const flowStatus = ref(0)
const rejected = ref(false)
const rejectOpinion = ref('')
const projects = ref([])

const meta = reactive({ orig: {}, dictChangeType: [], dictProjectType: [] })

const form = reactive({
  flowId: null, projectId: null, projectType: '',
  changeTypes: [], changeContent: '', changeReason: '',
  newAmount: null, newEndDate: '', newLeaderId: null
})

const mode = computed(() => (canEdit.value ? 'edit' : 'print'))
const canEdit = computed(() => [0, 1, 2, 4].includes(flowStatus.value))

function collectForm() {
  return {
    flowId: form.flowId,
    projectId: form.projectId,
    changeType: form.changeTypes.join(','),
    changeContent: form.changeContent,
    changeReason: form.changeReason,
    newAmount: has('项目经费') ? form.newAmount : null,
    newEndDate: has('执行周期') ? form.newEndDate : null,
    newLeaderId: has('项目负责人') ? form.newLeaderId : null
  }
}
const has = (t) => form.changeTypes.includes(t)

let changeTimer = null
function onChange() {
  clearTimeout(changeTimer)
  changeTimer = setTimeout(() => canEdit.value && form.projectId && doSave(false), 1500)
}

async function doSave(toast = true) {
  if (!form.changeTypes.length) return
  saving.value = true
  try {
    const fid = await saveChangeDraft(collectForm())
    form.flowId = fid
    flowStatus.value = (await flowDetail(fid)).flow.status
    if (String(route.params.flowId || '') !== String(fid)) {
      router.replace(`/hx/change/${fid}`)
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
  if (!form.changeTypes.length) {
    ElMessage.warning('请勾选变更事项')
    return
  }
  if (has('项目经费') && !form.newAmount) {
    ElMessage.warning('勾选「项目经费」须填写变更后经费')
    return
  }
  if (has('执行周期') && !form.newEndDate) {
    ElMessage.warning('勾选「执行周期」须选择变更后结束时间')
    return
  }
  if (has('项目负责人') && !form.newLeaderId) {
    ElMessage.warning('勾选「项目负责人」须选择变更后负责人')
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
      await submitChange(form.flowId)
      ElMessage.success('已提交;请在流程详情打印待签件')
    }
    router.replace(`/hx/flow/${form.flowId}`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function print() {
  window.open(`/print/change/${form.flowId}`, '_blank')
}

/* 选项目→实态填 A 区(经 flowDetail 拿完整实态) */
async function pickProject(pid) {
  const p = projects.value.find((x) => x.projectId === pid)
  if (!p) return
  await fillOrig(p)
}
async function fillOrig(p) {
  const d = await flowDetail(p.flowId)
  meta.orig = {
    approvalNo: d.project.approvalNo || '', name: d.project.name,
    leaderName: d.project.leaderName, leaderPhone: d.project.leaderPhone || '',
    clientName: d.hx.clientName, clientNature: d.hx.clientNature,
    contractAmount: d.hx.contractAmount,
    startDate: d.hx.startDate ? String(d.hx.startDate).slice(0, 10) : '',
    endDate: d.hx.endDate ? String(d.hx.endDate).slice(0, 10) : ''
  }
  form.projectType = d.hx.projectType || ''
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
  form.flowId = d.flow.id
  form.projectId = d.flow.projectId
  form.changeTypes = (d.flow.changeType || '').split(',').filter(Boolean)
  form.changeContent = d.flow.changeContent || ''
  form.changeReason = d.flow.changeReason || ''
  form.newAmount = d.flow.newAmount
  form.newEndDate = d.flow.newEndDate ? String(d.flow.newEndDate).slice(0, 10) : ''
  form.newLeaderId = d.flow.newLeaderId
  await fillOrig({ projectId: d.flow.projectId, flowId: d.flow.id })
  form.projectType = d.hx.projectType || ''
}

onMounted(async () => {
  const [ct, my] = await Promise.all([listDict('hx_change_type'), myProjects()])
  meta.dictChangeType = ct.map((x) => x.label)
  projects.value = (my || []).filter((p) => p.auditStatus === 2)
  if (route.params.flowId) {
    await loadDraft(Number(route.params.flowId))
  } else if (route.query.projectId) {
    /* 「发起变更」入口:预选项目直接带出 A 区实态 */
    const pid = Number(route.query.projectId)
    if (projects.value.some((p) => p.projectId === pid)) {
      form.projectId = pid
      await pickProject(pid)
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
