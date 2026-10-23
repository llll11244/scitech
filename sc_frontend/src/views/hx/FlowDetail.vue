<template>
  <div v-loading="loading" class="flow-detail">
    <template v-if="d">
      <!-- 退回意见醒目提示(spec §7.1:退回意见醒目) -->
      <el-alert v-if="d.flow.status === 4 && rejectOpinion" type="error" :closable="false" class="mb"
        :title="`已被科技处退回:${rejectOpinion.actionLabel}`"
        :description="`${rejectOpinion.opinion}(重交后原盖章件作废,需重新盖章回扫;可先修改数据再重交)`" />

      <!-- 数据滞后:回扫后填报数据又被修改(教师或科技处),已盖章扫描件对应旧数据 -->
      <el-alert v-if="dataStale" type="error" :closable="false" class="mb"
        title="填报数据在回扫后被修改:当前盖章扫描件对应旧数据,请重新打印盖章并回扫。" />

      <!-- 状态头 -->
      <el-card shadow="never" class="mb">
        <div class="head-bar">
          <el-steps :active="activeStep" align-center finish-status="success" class="steps">
            <el-step title="填报草稿" />
            <el-step title="科技处预审" />
            <el-step title="盖章回扫" />
            <el-step title="审核" />
            <el-step :title="d.flow.status === 4 ? '已退回' : '完成'" :status="d.flow.status === 4 ? 'error' : undefined" />
          </el-steps>
          <el-tag :type="statusTag.type" size="large">{{ statusTag.label }} · {{ flowLabel }}</el-tag>
        </div>
      </el-card>

      <el-row :gutter="14">
        <!-- 左:单据(只读 Sheet,与审批/打印同口径)+ 时间线 -->
        <el-col :span="14">
          <el-card shadow="never" class="mb preview-card">
            <template #header>
              <div class="bar">
                <el-radio-group v-if="deliverDocs.length > 1" v-model="curDoc" size="small">
                  <el-radio-button v-for="doc in deliverDocs" :key="doc.type" :value="doc.type">
                    {{ doc.label }}
                  </el-radio-button>
                </el-radio-group>
                <span v-else class="doc-title">{{ deliverDocs[0]?.label }}</span>
              </div>
            </template>
            <div class="preview-box">
              <template v-if="sheetReady && d.flow.flowType === 'register'">
                <RegisterSheet :model="model" mode="print" :meta="meta" />
              </template>
              <template v-else-if="sheetReady && d.flow.flowType === 'change'">
                <ChangeSheet :model="model" mode="print" :meta="meta" />
              </template>
              <template v-else-if="sheetReady">
                <AcceptanceSheet v-if="curDoc !== 'hx_report'" :model="model" mode="print" :meta="meta" />
                <ReportSheet v-else :model="model" mode="print" :meta="meta" />
              </template>
              <el-empty v-else description="单据加载中" :image-size="70" />
            </div>
          </el-card>

          <!-- 时间线 -->
          <el-card shadow="never">
            <template #header>流程时间线</template>
            <el-timeline v-if="records.length">
              <el-timeline-item v-for="r in records" :key="r.id" :timestamp="fmtTime(r.createTime)" placement="top"
                :type="r.action === 'withdraw' ? 'warning' : 'primary'">
                <div class="tl-action">{{ ACTION_LABEL[r.action] || r.action }} · {{ r.operatorName }}</div>
                <div v-if="r.opinion" class="tl-opinion">意见:{{ r.opinion }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无留痕" :image-size="70" />
          </el-card>
        </el-col>

        <!-- 右:交付 + 扫描件 + 操作 -->
        <el-col :span="10">
          <!-- 待签交付(HTML即交付):打印/另存 PDF 为主;旧单历史 Word 降级保留 -->
          <el-card shadow="never" class="mb">
            <template #header>待签文件</template>
            <template v-if="d.flow.status >= 1">
              <div class="deliver-row" v-for="doc in deliverDocs" :key="doc.type">
                <span class="doc-name">{{ doc.label }}</span>
                <el-button size="small" type="primary" @click="openPrint(doc)">
                  打印 / 另存为 PDF
                </el-button>
                <el-button v-if="hasLegacyDocx(doc)" size="small" @click="download(doc, 'docx')">
                  下载历史 Word(旧版)
                </el-button>
              </div>
            </template>
            <el-empty v-else description="提交后可打印待签件(浏览器打印 A4/另存 PDF)" :image-size="70" />
          </el-card>

          <!-- 扫描件区 -->
          <el-card shadow="never" class="mb">
            <template #header>盖章扫描件</template>
            <!-- 预审通过(5):可上传 -->
            <template v-if="d.flow.status === 5">
              <div class="scan-form">
                <el-select v-model="scanDocType" placeholder="扫描件类型" size="small" class="scan-select">
                  <el-option v-for="t in scanDocTypes" :key="t" :label="t" :value="t" />
                </el-select>
                <el-upload :show-file-list="false" :auto-upload="false" accept=".pdf,.png,.jpg,.jpeg"
                  :on-change="(f) => onScanPicked(f)">
                  <el-button size="small" type="primary" :loading="scanning">
                    {{ scanning ? '上传中…' : '上传扫描件' }}
                  </el-button>
                </el-upload>
              </div>
              <div class="tip">预审已通过:请打印单据、线下盖章后扫描上传;重传同类型会覆盖旧件。上传后进入科技处审核。</div>
            </template>
            <!-- 已回扫:列表(查看/下载走附件内容接口) -->
            <template v-else-if="d.flow.status >= 2">
              <div v-for="a in scans" :key="a.id" class="scan-item">
                <el-tag size="small">{{ a.docType }}</el-tag>
                <span class="scan-name">{{ a.fileName }}</span>
                <span class="scan-time">{{ fmtTime(a.createTime) }}</span>
                <el-button link type="primary" size="small" @click="viewAtt(a)">查看</el-button>
                <el-button link size="small" @click="downAtt(a)">下载</el-button>
              </div>
              <el-empty v-if="!scans.length" description="暂无扫描件" :image-size="60" />
              <div v-if="d.flow.status === 2" class="tip">已回扫,待科技处审核。如需更换盖章件,可直接重传覆盖。</div>
            </template>
            <!-- 预审中(1)/其他:未到盖章阶段 -->
            <el-empty v-else-if="d.flow.status === 1" description="科技处预审通过后方可打印盖章并上传扫描件" :image-size="70" />
            <el-empty v-else description="提交并线下盖章后上传扫描件" :image-size="70" />
          </el-card>

          <!-- 合同扫描件(hx_apply,登记向导上传;本项目级附件,查看/下载同扫描件) -->
          <el-card shadow="never" class="mb">
            <template #header>合同扫描件</template>
            <div v-for="a in contracts" :key="a.id" class="scan-item">
              <el-tag size="small">{{ a.docType }}</el-tag>
              <span class="scan-name">{{ a.fileName }}</span>
              <span class="scan-time">{{ fmtTime(a.createTime) }}</span>
              <el-button link type="primary" size="small" @click="viewAtt(a)">查看</el-button>
              <el-button link size="small" @click="downAtt(a)">下载</el-button>
            </div>
            <el-empty v-if="!contracts.length" description="暂无合同扫描件" :image-size="60" />
          </el-card>

          <!-- 底部操作 -->
          <el-card shadow="never">
            <template #header>操作</template>
            <div class="op-row">
              <el-button v-if="[0, 1, 2, 4, 5].includes(d.flow.status)" type="primary" @click="editEntry">
                {{ d.flow.status === 0 ? '继续编辑' : d.flow.status === 4 ? '修改重交' : '修改填报' }}
              </el-button>
              <el-button v-if="d.flow.status === 1" @click="doWithdraw('撤回后回到草稿,可修改后重新提交,确定撤回?')">
                撤回(回草稿)
              </el-button>
              <el-button v-if="d.flow.status === 2" @click="doWithdraw('撤回后回到预审通过状态,可重新上传扫描件(不须再预审),确定撤回?')">
                撤回(回待盖章)
              </el-button>
              <!-- 不改数据直接重交(4→1,旧扫描件作废,spec §3.2) -->
              <el-button v-if="d.flow.status === 4" @click="doResubmit">直接重交</el-button>
              <!-- 预审通过(5)改数据后重新提交:5→1 重新预审(尚未传件,无作废) -->
              <el-button v-if="d.flow.status === 5" @click="doResubmit">重新提交(重新预审)</el-button>
              <!-- 取消变更(五轮反馈):在途变更终止,作废后可发新变更;审核中(2)须先撤回 -->
              <el-button v-if="d.flow.flowType === 'change' && [0, 1, 4, 5].includes(d.flow.status)"
                type="danger" plain @click="doCancelChange">取消变更</el-button>
              <el-button @click="$router.push('/hx')">返回列表</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelChange, downloadAttachment, downloadDeliver, flowDetail, resubmit, uploadScan, withdraw
} from '../../api/hx'
import { buildSheetModel } from '../../composables/sheetModel'
import AcceptanceSheet from '../../components/hxsheet/AcceptanceSheet.vue'
import ChangeSheet from '../../components/hxsheet/ChangeSheet.vue'
import RegisterSheet from '../../components/hxsheet/RegisterSheet.vue'
import ReportSheet from '../../components/hxsheet/ReportSheet.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const d = ref(null)          // FlowDetailVO
const scanDocType = ref('')
const scanning = ref(false)

/* 只读单据(与审批/打印同口径):sheetReady 守卫防空 model 崩子树 */
const sheetReady = ref(false)
const curDoc = ref('hx_acceptance')
const model = reactive({})
const meta = reactive({ orig: {} })

const ACTION_LABEL = {
  submit: '提交', withdraw: '撤回', upload_scan: '上传扫描件', resubmit: '重新提交',
  precheck: '科技处预审通过', approve: '审批通过', verify: '验收通过', reject: '退回',
  precheck_reject: '预审退回', admin_edit: '科技处修正'
}

/** 流程类型对应允许的扫描件类型(与后端 validateScanDocType 同口径) */
const SCAN_DOC_ALLOW = {
  register: ['登记表'],
  change: ['变更表'],
  acceptance: ['验收登记表', '验收报告']
}

const flowId = computed(() => Number(route.params.flowId))
const flowLabel = computed(() => ({ register: '登记表', change: '变更表', acceptance: '验收单' }[d.value?.flow?.flowType] || '流程单'))
const statusTag = computed(() => ({
  0: { label: '草稿', type: 'info' },
  1: { label: '科技处预审中', type: 'warning' },
  2: { label: '已回扫·审核中', type: 'primary' },
  3: { label: '审核通过', type: 'success' },
  4: { label: '已退回', type: 'danger' },
  5: { label: '预审通过·待盖章回扫', type: 'primary' }
}[d.value?.flow?.status] || { label: '—', type: 'info' }))
const activeStep = computed(() => {
  const s = d.value?.flow?.status ?? 0
  // 0草稿/1预审/5盖章回扫/2审核/3完成;4 退回停在其所处阶段
  return s === 4 ? 3 : s === 5 ? 2 : s === 2 ? 3 : s
})
const records = computed(() => [...(d.value?.records || [])].reverse())
const scans = computed(() => (d.value?.attachments || []).filter((a) => a.bizType === 'hx_scan'))
/** 合同扫描件(hx_apply):项目级附件,登记向导上传,教师可查看/下载自己的合同扫描件 */
const contracts = computed(() => (d.value?.attachments || []).filter((a) => a.bizType === 'hx_apply'))
const scanDocTypes = computed(() => SCAN_DOC_ALLOW[d.value?.flow?.flowType] || [])

/** 交付文档:验收单两张,其余一张 */
const deliverDocs = computed(() => {
  if (d.value?.flow?.flowType === 'acceptance') {
    return [
      { type: 'hx_acceptance', label: '验收登记表' },
      { type: 'hx_report', label: '验收报告' }
    ]
  }
  return [{ type: '', label: flowLabel.value }]
})

/** HTML即交付:打印=新标签只读页自动弹打印框(验收单 doc 参数区分两张) */
function openPrint(doc) {
  const q = doc.type ? `?doc=${doc.type}` : ''
  window.open(`/print/${d.value.flow.flowType}/${flowId.value}${q}`, '_blank')
}

/** 旧单遗留的待签 docx(HTML转正前生成):存在才显示「下载历史 Word」 */
function hasLegacyDocx(doc) {
  const types = ['hx_docx', ...(doc.type === 'hx_report' ? ['hx_report_docx'] : [])]
  return (d.value?.attachments || []).some((a) => types.includes(a.bizType))
}

/** 数据滞后判定:回扫后数据又被改(updateTime>scanTime,秒级精度)→提示重新打印盖章回扫 */
const dataStale = computed(() => {
  const f = d.value?.flow
  return f && f.status === 2 && f.scanTime && f.updateTime
    && String(f.updateTime) > String(f.scanTime)
})

const fmtTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

/** 退回态:最近一条退回留痕意见(d.records 为 orderByAsc(id) 最旧在前,取末元素=最新一次退回) */
const rejectOpinion = computed(() => {
  const rs = (d.value?.records || []).filter((r) => r.action === 'reject' || r.action === 'precheck_reject')
  const last = rs[rs.length - 1]
  if (!last) return null
  return { opinion: last.opinion || '', actionLabel: ACTION_LABEL[last.action] || '退回' }
})

async function load() {
  loading.value = true
  sheetReady.value = false
  try {
    d.value = await flowDetail(flowId.value)
    const built = await buildSheetModel(d.value, d.value.flow.flowType)
    Object.keys(model).forEach((k) => delete model[k])
    Object.assign(model, built.model)
    Object.keys(meta).forEach((k) => delete meta[k])
    Object.assign(meta, built.meta)
    curDoc.value = deliverDocs.value[0]?.type || ''
    scanDocType.value = scanDocTypes.value[0] || ''
  } catch (e) {
    console.error('[FlowDetail] 加载失败', e)
  } finally {
    loading.value = false
    sheetReady.value = true
  }
}

/** 下载历史待签 Word(旧单遗留;终版归档件已随 HTML 即交付退役) */
async function download(doc, kind) {
  try {
    const blob = await downloadDeliver(flowId.value, kind, doc.type || undefined)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${d.value.project?.name || '横向项目'}_${doc.label}.${kind}`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function onScanPicked(file) {
  const raw = file.raw
  if (!raw) return
  if (!scanDocType.value) {
    ElMessage.warning('请先选择扫描件类型')
    return
  }
  scanning.value = true
  try {
    await uploadScan(flowId.value, raw, scanDocType.value)
    ElMessage.success('扫描件已上传')
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    scanning.value = false
  }
}

/** 扫描件查看:新标签页内联展示(PDF/图片浏览器原生渲染) */
async function viewAtt(a) {
  try {
    const blob = await downloadAttachment(a.id)
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000) // 延迟释放,给新标签加载留时间
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/** 扫描件下载:沿用附件原名 */
async function downAtt(a) {
  try {
    const blob = await downloadAttachment(a.id)
    const url = URL.createObjectURL(blob)
    const el = document.createElement('a')
    el.href = url
    el.download = a.fileName || '扫描件'
    el.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function doWithdraw(msg) {
  try {
    await ElMessageBox.confirm(msg, '提示', { type: 'warning' })
  } catch { return }
  try {
    await withdraw(flowId.value)
    ElMessage.success('已撤回')
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/** 退回单直接重交(4→1,旧扫描件作废)/预审通过后改数据重新提交(5→1 重新预审) */
async function doResubmit() {
  const isRejected = d.value?.flow?.status === 4
  try {
    await ElMessageBox.confirm(isRejected
      ? '直接重交将按当前数据重新提交预审,原盖章件作废(需重新盖章回扫),确定重交?'
      : '重新提交后本单将回到科技处预审(当前数据生效,预审通过前不可上传扫描件),确定提交?',
      '提示', { type: 'warning' })
  } catch { return }
  try {
    await resubmit(flowId.value)
    ElMessage.success(isRejected ? '已重交,请等待科技处审核' : '已重新提交,请等待科技处预审')
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/** 审批前编辑入口(0/1/2/4/5):跳对应填单页 */
function editEntry() {
  const t = d.value?.flow?.flowType
  if (t === 'register') router.push(`/hx/register/${flowId.value}`)
  else if (t === 'change') router.push(`/hx/change/${flowId.value}`)
  else if (t === 'acceptance') router.push(`/hx/acceptance/${flowId.value}`)
}

/** 取消在途变更:作废流程单+扫描件(变更未生效项目数据不变),作废后可发新变更 */
async function doCancelChange() {
  try {
    await ElMessageBox.confirm(
      '取消后本变更单及其扫描件作废(变更未生效,项目数据不变),取消后可重新发起新变更,确定取消?',
      '取消变更', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想' })
  } catch { return }
  try {
    await cancelChange(flowId.value)
    ElMessage.success('变更已取消')
    router.replace('/hx')
  } catch (e) {
    /* 拦截器已提示(审核中会提示先撤回) */
  }
}

onMounted(load)
</script>

<script>
export default { name: 'FlowDetail' }
</script>

<style scoped>
.flow-detail {
  min-height: 200px;
}

.mb {
  margin-bottom: 14px;
}

.head-bar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.steps {
  flex: 1;
}

.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.doc-title {
  font-size: 14px;
  font-weight: 600;
}

.preview-card :deep(.el-card__body) {
  padding: 10px;
}

.preview-box {
  height: calc(100vh - 320px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px 8px;
}

.tl-action {
  font-size: 14px;
  color: #303133;
}

.tl-opinion {
  margin-top: 4px;
  font-size: 13px;
  color: #f56c6c;
}

.deliver-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
}

.deliver-row:last-child {
  border-bottom: none;
}

.doc-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
}

.scan-form {
  display: flex;
  gap: 10px;
  align-items: center;
}

.scan-select {
  width: 160px;
}

.scan-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
}

.scan-item:last-child {
  border-bottom: none;
}

.scan-name {
  flex: 1;
  color: #303133;
}

.scan-time {
  color: #909399;
  font-size: 12px;
}

.op-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.final-title {
  margin: 10px 0 2px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
  font-size: 13px;
  font-weight: 600;
  color: #67c23a;
}
</style>
