<template>
  <div v-loading="loading" class="audit-detail">
    <template v-if="d">
      <!-- 顶栏:单据标识 + 审批动作 -->
      <el-card shadow="never" class="mb">
        <div class="head-bar">
          <div class="head-info">
            <el-tag :type="TYPE_TAG[d.flow.flowType]?.type || 'info'" effect="plain">
              {{ TYPE_TAG[d.flow.flowType]?.label || d.flow.flowType }}
            </el-tag>
            <span class="name">{{ d.project?.name }}</span>
            <el-tag :type="STATUS[d.flow.status]?.type" size="small">{{ STATUS[d.flow.status]?.label }}</el-tag>
            <el-tag v-if="d.flow.status === 1" size="small" type="warning" effect="plain">可预检</el-tag>
          </div>
          <div>
            <el-button v-if="d.flow.status === 1 || d.flow.status === 2" type="warning" plain
              @click="editMode = !editMode">
              {{ editMode ? '退出修正' : '修正填报' }}
            </el-button>
            <el-button v-if="d.flow.status === 2" type="primary" @click="approveVisible = true">
              {{ d.flow.flowType === 'acceptance' ? '通过(录核定额)' : '通过' }}
            </el-button>
            <el-button v-if="d.flow.status === 1 || d.flow.status === 2" type="danger" plain
              @click="rejectVisible = true">
              退回
            </el-button>
            <el-button @click="$router.push('/admin/audit')">返回列表</el-button>
          </div>
        </div>
      </el-card>

      <!-- 数据滞后警示:回扫后数据又被改(教师/admin),盖章扫描件对应旧数据 -->
      <el-alert v-if="dataStale" type="error" :closable="false" class="mb"
        title="填报数据在回扫后被修改:当前盖章扫描件对应旧数据,请要求教师重新打印盖章回扫或认可差异后再通过。" />

      <el-row :gutter="14">
        <!-- 左:单据(HTML即交付:只读/修正共用同一 Sheet;打印=新标签) -->
        <el-col :span="14">
          <el-card shadow="never" class="preview-card">
            <template #header>
              <div class="bar">
                <el-radio-group v-if="docs.length > 1" v-model="curDoc" size="small">
                  <el-radio-button v-for="doc in docs" :key="doc.type" :value="doc.type">
                    {{ doc.label }}
                  </el-radio-button>
                </el-radio-group>
                <span v-else class="doc-title">{{ docs[0]?.label }}</span>
                <div class="dl">
                  <el-button v-if="editMode" size="small" type="warning" :loading="savingEdit"
                    @click="doAdminSave">保存修正(留痕+通知教师)</el-button>
                  <el-button size="small" type="primary" @click="openPrint">打印 / 另存为 PDF</el-button>
                  <el-button v-if="hasLegacyDocx" size="small" @click="download('docx')">下载历史 Word</el-button>
                </div>
              </div>
            </template>
            <div class="preview-box" :class="{ editing: editMode }">
              <template v-if="sheetReady && d.flow.flowType === 'register'">
                <RegisterSheet :model="model" :mode="editMode ? 'edit' : 'print'" :meta="meta" />
              </template>
              <template v-else-if="sheetReady && d.flow.flowType === 'change'">
                <ChangeSheet :model="model" :mode="editMode ? 'edit' : 'print'" :meta="meta" />
              </template>
              <template v-else-if="sheetReady">
                <AcceptanceSheet v-if="curDoc !== 'hx_report'" :model="model"
                  :mode="editMode ? 'edit' : 'print'" :meta="meta" />
                <ReportSheet v-else :model="model" :mode="editMode ? 'edit' : 'print'" :meta="meta" />
              </template>
              <el-empty v-else description="单据加载中" :image-size="70" />
            </div>
          </el-card>
        </el-col>

        <!-- 右:摘要/对照/扫描件/时间线 -->
        <el-col :span="10">
          <!-- 申请摘要 -->
          <el-card shadow="never" class="mb">
            <template #header>申请摘要</template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="项目名称">{{ d.project?.name }}</el-descriptions-item>
              <el-descriptions-item label="负责人">{{ d.project?.leaderName }}</el-descriptions-item>
              <template v-if="d.hx">
                <el-descriptions-item label="委托单位">
                  {{ d.hx.clientName }}({{ d.hx.clientNature }})
                </el-descriptions-item>
                <el-descriptions-item label="合同经费(元)">{{ fmtMoney(d.hx.contractAmount) }}</el-descriptions-item>
                <el-descriptions-item label="合同起止">
                  {{ String(d.hx.startDate || '').slice(0, 10) }} 至 {{ String(d.hx.endDate || '').slice(0, 10) }}
                </el-descriptions-item>
              </template>
              <template v-if="d.flow.flowType === 'change'">
                <el-descriptions-item label="变更类型">{{ d.flow.changeType }}</el-descriptions-item>
                <el-descriptions-item label="变更内容">{{ d.flow.changeContent || '—' }}</el-descriptions-item>
                <el-descriptions-item v-if="d.flow.newAmount != null" label="变更后经费(元)">
                  {{ fmtMoney(d.flow.newAmount) }}
                </el-descriptions-item>
                <el-descriptions-item v-if="d.flow.newEndDate" label="变更后结束时间">
                  {{ String(d.flow.newEndDate).slice(0, 10) }}
                </el-descriptions-item>
                <el-descriptions-item v-if="d.newLeaderName" label="变更后负责人">
                  {{ d.newLeaderName }}
                </el-descriptions-item>
                <el-descriptions-item label="变更原因">{{ d.flow.changeReason || '—' }}</el-descriptions-item>
              </template>
              <template v-if="d.flow.flowType === 'acceptance'">
                <el-descriptions-item label="到账累计(元)">{{ fmtMoney(d.receivedTotal) }}</el-descriptions-item>
                <el-descriptions-item label="使用经费(元)">{{ fmtMoney(d.flow.usedAmount) }}</el-descriptions-item>
                <el-descriptions-item label="是否按期">{{ d.flow.isOnTime === 1 ? '按期' : '延期' }}</el-descriptions-item>
                <el-descriptions-item label="成果形式">{{ d.flow.outcomeForm || '—' }}</el-descriptions-item>
              </template>
            </el-descriptions>
          </el-card>

          <!-- 退回态变更单:before/after 对照(before_json=提交时快照) -->
          <el-card v-if="diffRows.length" shadow="never" class="mb">
            <template #header>变更前 → 变更后 对照</template>
            <el-table :data="diffRows" size="small" border>
              <el-table-column prop="field" label="字段" width="110" />
              <el-table-column prop="before" label="变更前" min-width="110">
                <template #default="{ row }">{{ row.before || '—' }}</template>
              </el-table-column>
              <el-table-column prop="after" label="变更后" min-width="110">
                <template #default="{ row }">{{ row.after || '—' }}</template>
              </el-table-column>
            </el-table>
            <div class="tip">变更前=提交时快照(before_json);变更后=当前申请值。通过后按变更后值改写生效。</div>
          </el-card>

          <!-- 盖章扫描件 -->
          <el-card shadow="never" class="mb">
            <template #header>盖章扫描件</template>
            <div v-for="a in scans" :key="a.id" class="scan-item">
              <el-tag size="small">{{ a.docType }}</el-tag>
              <span class="scan-name">{{ a.fileName }}</span>
              <span class="scan-time">{{ fmtTime(a.createTime) }}</span>
              <el-button link type="primary" size="small" @click="viewAtt(a)">查看</el-button>
              <el-button link size="small" @click="downAtt(a)">下载</el-button>
            </div>
            <el-empty v-if="!scans.length" description="暂无扫描件(教师盖章回扫后出现)" :image-size="60" />
          </el-card>

          <!-- 合同扫描件 -->
          <el-card shadow="never" class="mb">
            <template #header>合同扫描件</template>
            <div v-for="a in contracts" :key="a.id" class="scan-item">
              <el-tag size="small">{{ a.docType }}</el-tag>
              <span class="scan-name">{{ a.fileName }}</span>
              <span class="scan-time">{{ fmtTime(a.createTime) }}</span>
              <el-button link type="primary" size="small" @click="viewAtt(a)">查看</el-button>
              <el-button link size="small" @click="downAtt(a)">下载</el-button>
            </div>
            <el-empty v-if="!contracts.length" description="暂无合同扫描件(教师在登记页上传)" :image-size="60" />
          </el-card>

          <!-- 时间线 -->
          <el-card shadow="never">
            <template #header>流转时间线</template>
            <el-timeline v-if="records.length">
              <el-timeline-item v-for="r in records" :key="r.id" :timestamp="fmtTime(r.createTime)" placement="top"
                :type="r.action.includes('reject') ? 'danger' : r.action === 'withdraw' ? 'warning' : 'primary'">
                <div class="tl-action">{{ ACTION_LABEL[r.action] || r.action }} · {{ r.operatorName }}</div>
                <div v-if="r.opinion" class="tl-opinion">意见:{{ r.opinion }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无留痕" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 通过弹窗:验收须录核定到账金额 -->
      <el-dialog v-model="approveVisible" title="审批通过" width="460px">
        <template v-if="d.flow.flowType === 'acceptance'">
          <el-form label-width="130px">
            <el-form-item label="核定到账金额(元)" required>
              <el-input-number v-model="verifiedAmount" :min="0" :precision="2" :controls="false"
                placeholder="验收确认的到账核定额" class="w-full" />
            </el-form-item>
          </el-form>
          <el-alert type="info" :closable="false"
            title="通过即结题:写入核定额并通知教师;终版=通过后打印只读页(带核定额/核定人)。" />
        </template>
        <el-alert v-else type="info" :closable="false"
          :title="`确认通过该${TYPE_TAG[d.flow.flowType]?.label || '单据'}?${APPROVE_TIP[d.flow.flowType] || ''}`" />
        <template #footer>
          <el-button @click="approveVisible = false">取消</el-button>
          <el-button type="primary" :loading="acting" @click="doApprove">确认通过</el-button>
        </template>
      </el-dialog>

      <!-- 退回弹窗:意见必填;预检勾选仅 status=1 -->
      <el-dialog v-model="rejectVisible" title="退回" width="520px">
        <el-form label-width="90px">
          <el-form-item label="退回意见" required>
            <el-input v-model="rejectOpinion" type="textarea" :rows="4" maxlength="300" show-word-limit
              placeholder="退回原因将通知教师,修改后重交(原盖章件作废)" />
          </el-form-item>
          <el-form-item v-if="d.flow.status === 1">
            <el-checkbox v-model="isPrecheck">盖章前预检退回(单据尚未盖章,提示教师勿打印盖章)</el-checkbox>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="rejectVisible = false">取消</el-button>
          <el-button type="danger" :loading="acting" @click="doReject">确认退回</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<!--
  科技处审批页(HTML转正 2026-10-21):左=单据只读 Sheet(与打印页同口径),
  「修正填报」(status 1/2)切 edit 模式,保存走 admin-save(留痕 admin_edit+通知教师,
  与提交同强度金额/字数校验);打印=新标签只读页;数据滞后(回扫后被改)红条警示。
-->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminSave, downloadAttachment, downloadDeliver, flowDetail, rejectFlow, reviewFlow } from '../../api/hx'
import { buildSheetModel, collectSheetDto } from '../../composables/sheetModel'
import AcceptanceSheet from '../../components/hxsheet/AcceptanceSheet.vue'
import ChangeSheet from '../../components/hxsheet/ChangeSheet.vue'
import RegisterSheet from '../../components/hxsheet/RegisterSheet.vue'
import ReportSheet from '../../components/hxsheet/ReportSheet.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const d = ref(null)
const sheetReady = ref(false)   // model/meta 就绪守卫:d 赋值即触发重渲染,空 model 会让 Sheet 渲染崩
const curDoc = ref('hx_acceptance')
const editMode = ref(false)
const savingEdit = ref(false)

const model = reactive({})
const meta = reactive({ orig: {} })

const ACTION_LABEL = {
  submit: '提交', withdraw: '撤回', upload_scan: '上传扫描件', resubmit: '重新提交',
  approve: '审批通过', verify: '验收通过', reject: '退回', precheck_reject: '盖章前预检退回',
  admin_edit: '科技处修正'
}
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
const APPROVE_TIP = {
  register: '通过即入台账(分配项目编号、状态改在研)并通知教师。',
  change: '通过即按变更内容改写项目字段并通知教师。'
}

const flowId = computed(() => Number(route.params.flowId))
const records = computed(() => [...(d.value?.records || [])].reverse())
const scans = computed(() => (d.value?.attachments || []).filter((a) => a.bizType === 'hx_scan'))
const contracts = computed(() => (d.value?.attachments || []).filter((a) => a.bizType === 'hx_apply'))

/** 单据文档:验收单两张,其余一张 */
const docs = computed(() => {
  if (d.value?.flow?.flowType === 'acceptance') {
    return [
      { type: 'hx_acceptance', label: '验收登记表' },
      { type: 'hx_report', label: '验收报告' }
    ]
  }
  return [{ type: '', label: TYPE_TAG[d.value?.flow?.flowType]?.label || '单据' }]
})

/** 旧单遗留 docx(HTML转正前生成) */
const hasLegacyDocx = computed(() => (d.value?.attachments || [])
  .some((a) => ['hx_docx', 'hx_report_docx'].includes(a.bizType)))

/** 数据滞后:回扫后数据又被改(updateTime>scanTime,秒级精度) */
const dataStale = computed(() => {
  const f = d.value?.flow
  return f && f.status === 2 && f.scanTime && f.updateTime && String(f.updateTime) > String(f.scanTime)
})

/** 退回态变更单 before/after 对照 */
const diffRows = computed(() => {
  const f = d.value?.flow
  if (!f || f.flowType !== 'change' || f.status !== 4 || !f.beforeJson) return []
  let before = {}
  try {
    before = JSON.parse(f.beforeJson)
  } catch {
    return []
  }
  const rows = []
  if (f.newAmount != null) {
    rows.push({ field: '合同经费(元)', before: before.contractAmount ?? '', after: f.newAmount })
  }
  if (f.newEndDate) {
    rows.push({ field: '结束时间', before: before.endDate ? String(before.endDate).slice(0, 10) : '',
      after: String(f.newEndDate).slice(0, 10) })
  }
  if (f.newLeaderId) {
    rows.push({ field: '负责人', before: before.leaderName || d.value.project?.leaderName,
      after: d.value.newLeaderName || '' })
  }
  rows.push({ field: '项目名称', before: before.name || '', after: d.value.project?.name || '' })
  return rows
})

const fmtMoney = (v) => (v === null || v === undefined ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
const fmtTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

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
    curDoc.value = docs.value[0]?.type || ''
    if (![1, 2].includes(d.value.flow.status)) {
      editMode.value = false
    }
    sheetReady.value = true
  } catch (e) {
    console.error('[AuditDetail] 加载失败', e)
    ElMessage.error('单据加载失败:' + (e && e.message ? e.message : '请看控制台'))
  } finally {
    loading.value = false
  }
}

/** admin 代改保存:与教师提交同强度校验由后端强制;留痕 admin_edit+通知教师 */
async function doAdminSave() {
  savingEdit.value = true
  try {
    const dto = collectSheetDto(d.value.flow.flowType, model, flowId.value, d.value.flow.projectId)
    await adminSave(flowId.value, dto)
    ElMessage.success('已修正并通知教师')
    editMode.value = false
    await load()
  } catch (e) {
    /* 拦截器已提示(金额/字数校验失败会在此报错) */
  } finally {
    savingEdit.value = false
  }
}

function openPrint() {
  const q = curDoc.value && d.value.flow.flowType === 'acceptance' ? `?doc=${curDoc.value}` : ''
  window.open(`/print/${d.value.flow.flowType}/${flowId.value}${q}`, '_blank')
}

async function download(kind) {
  try {
    const blob = await downloadDeliver(flowId.value, kind,
      d.value.flow.flowType === 'acceptance' ? curDoc.value || undefined : undefined)
    saveBlob(blob, `${d.value.project?.name || '待签件'}.${kind}`)
  } catch (e) { /* 拦截器已提示 */ }
}

function saveBlob(blob, name) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

async function viewAtt(a) {
  try {
    const blob = await downloadAttachment(a.id)
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) { /* 拦截器已提示 */ }
}

async function downAtt(a) {
  try {
    const blob = await downloadAttachment(a.id)
    saveBlob(blob, a.fileName || '扫描件')
  } catch (e) { /* 拦截器已提示 */ }
}

/* ---------- 审批动作 ---------- */
const approveVisible = ref(false)
const rejectVisible = ref(false)
const verifiedAmount = ref(null)
const rejectOpinion = ref('')
const isPrecheck = ref(false)
const acting = ref(false)

async function doApprove() {
  if (d.value.flow.flowType === 'acceptance'
    && (verifiedAmount.value === null || verifiedAmount.value === undefined)) {
    ElMessage.warning('请录入核定到账金额')
    return
  }
  acting.value = true
  try {
    await reviewFlow(flowId.value,
      d.value.flow.flowType === 'acceptance' ? verifiedAmount.value : undefined)
    ElMessage.success('已通过')
    approveVisible.value = false
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    acting.value = false
  }
}

async function doReject() {
  const opinion = rejectOpinion.value.trim()
  if (!opinion) {
    ElMessage.warning('退回意见必填')
    return
  }
  acting.value = true
  try {
    await rejectFlow(flowId.value, isPrecheck.value ? `盖章前预检:${opinion}` : opinion, isPrecheck.value)
    ElMessage.success('已退回,已通知教师')
    rejectVisible.value = false
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<script>
export default { name: 'AuditDetail' }
</script>

<style scoped>
.mb {
  margin-bottom: 14px;
}

.head-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.head-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-card :deep(.el-card__body) {
  padding: 10px;
}

.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.doc-title {
  font-size: 14px;
  font-weight: 600;
}

.preview-box {
  height: calc(100vh - 250px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px 8px;
}

.preview-box.editing {
  background: #FDF6EC;
  border-color: #e6a23c;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scan-time {
  color: #909399;
  font-size: 12px;
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

.tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
