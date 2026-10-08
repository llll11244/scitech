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

      <el-row :gutter="14">
        <!-- 左:待签 Word 预览(决策#36 直出 Word,DocxPane 渲染) -->
        <el-col :span="14">
          <el-card shadow="never" class="preview-card">
            <template #header>
              <div class="bar">
                <el-radio-group v-if="deliverDocs.length > 1" v-model="curDoc" size="small" @change="loadPreview">
                  <el-radio-button v-for="doc in deliverDocs" :key="doc.type" :value="doc.type">
                    {{ doc.label }}
                  </el-radio-button>
                </el-radio-group>
                <span v-else class="doc-title">{{ deliverDocs[0]?.label }}</span>
                <div class="dl">
                  <el-button size="small" type="primary" @click="download('docx')">下载 Word</el-button>
                  <el-button v-if="pdfVisible" size="small" @click="download('pdf')">下载 PDF</el-button>
                  <!-- 终版:仅验收确认(status=3)后存在,spec §6.2 -->
                  <template v-if="d.flow.flowType === 'acceptance' && d.flow.status === 3">
                    <el-button size="small" type="success" @click="downloadFinal('docx')">终版 Word</el-button>
                    <el-button v-if="pdfVisible" size="small" type="success" plain
                      @click="downloadFinal('pdf')">终版 PDF</el-button>
                  </template>
                </div>
              </div>
            </template>
            <div class="preview-box">
              <DocxPane :blob="previewBlob" />
              <el-empty v-if="!previewBlob && !previewLoading" description="提交后可预览待签 Word" :image-size="70" />
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

          <!-- 合同扫描件(Task25 hx_apply,登记向导上传;后端已放行 admin 调阅) -->
          <el-card shadow="never" class="mb">
            <template #header>合同扫描件</template>
            <div v-for="a in contracts" :key="a.id" class="scan-item">
              <el-tag size="small">{{ a.docType }}</el-tag>
              <span class="scan-name">{{ a.fileName }}</span>
              <span class="scan-time">{{ fmtTime(a.createTime) }}</span>
              <el-button link type="primary" size="small" @click="viewAtt(a)">查看</el-button>
              <el-button link size="small" @click="downAtt(a)">下载</el-button>
            </div>
            <el-empty v-if="!contracts.length" description="暂无合同扫描件(教师在登记向导上传)" :image-size="60" />
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
            title="通过即结题:写入核定额、渲染终版两张(验收登记表+验收报告)并通知教师。" />
        </template>
        <el-alert v-else type="info" :closable="false"
          :title="`确认通过该${TYPE_TAG[d.flow.flowType]?.label || '单据'}?${APPROVE_TIP[d.flow.flowType] || ''}`" />
        <template #footer>
          <el-button @click="approveVisible = false">取消</el-button>
          <el-button type="primary" :loading="acting" @click="doApprove">确认通过</el-button>
        </template>
      </el-dialog>

      <!-- 退回弹窗:意见必填;预检勾选仅 status=1(盖章前预检,决策#18) -->
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
  科技处审批页(spec §7.2):左待签 Word 预览(deliver docx 流→DocxPane,决策#36),
  右摘要/退回对照/扫描件/时间线;[通过](验收录核定额)/[退回](意见必填,status=1/2 均可,
  预检勾选仅 status=1——后端不校验该语义,前端守);数据源=教师端同一 GET /hx/{flowId}(admin 直查)。
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  downloadAttachment, downloadDeliver, downloadDeliverFinal, flowDetail, rejectFlow, reviewFlow
} from '../../api/hx'
import DocxPane from '../hx/DocxPane.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const d = ref(null)
const curDoc = ref('hx_acceptance')

const ACTION_LABEL = {
  submit: '提交', withdraw: '撤回', upload_scan: '上传扫描件', resubmit: '重新提交',
  approve: '审批通过', verify: '验收通过', reject: '退回', precheck_reject: '盖章前预检退回'
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
const pdfVisible = computed(() => d.value?.deliverPdfVisible === 'true')
const records = computed(() => [...(d.value?.records || [])].reverse())
const scans = computed(() => (d.value?.attachments || []).filter((a) => a.bizType === 'hx_scan'))
/** 合同扫描件(hx_apply):项目级附件,教师登记向导上传,admin 可调阅(查看/下载同 hx_scan) */
const contracts = computed(() => (d.value?.attachments || []).filter((a) => a.bizType === 'hx_apply'))

/** 预览文档:验收单两张,其余一张 */
const deliverDocs = computed(() => {
  if (d.value?.flow?.flowType === 'acceptance') {
    return [
      { type: 'hx_acceptance', label: '验收登记表' },
      { type: 'hx_report', label: '验收报告' }
    ]
  }
  return [{ type: '', label: TYPE_TAG[d.value?.flow?.flowType]?.label || '待签件' }]
})

/** 退回态变更单 before/after 对照(before_json 提交时快照 vs 当前申请值) */
const diffRows = computed(() => {
  const f = d.value?.flow
  if (!f || f.flowType !== 'change' || f.status !== 4 || !f.beforeJson) return []
  let before = {}
  try {
    before = JSON.parse(f.beforeJson)
  } catch {
    return []
  }
  const hx = d.value.hx || {}
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

/* ---------- 加载+预览 ---------- */
const previewBlob = ref(null)
const previewLoading = ref(false)

async function load() {
  loading.value = true
  try {
    d.value = await flowDetail(flowId.value)
    curDoc.value = deliverDocs.value[0]?.type || ''
    await loadPreview()
  } finally {
    loading.value = false
  }
}

async function loadPreview() {
  if (d.value?.flow?.status === 0) return // 草稿无待签件
  previewLoading.value = true
  try {
    previewBlob.value = await downloadDeliver(flowId.value, 'docx', curDoc.value || undefined)
  } catch (e) {
    previewBlob.value = null
  } finally {
    previewLoading.value = false
  }
}

async function download(kind) {
  try {
    const blob = await downloadDeliver(flowId.value, kind, curDoc.value || undefined)
    saveBlob(blob, `${d.value.project?.name || '待签件'}.${kind}`)
  } catch (e) { /* 拦截器已提示 */ }
}

async function downloadFinal(kind) {
  try {
    const blob = await downloadDeliverFinal(flowId.value, kind, curDoc.value || undefined)
    saveBlob(blob, `${d.value.project?.name || '终版'}_终版.${kind}`)
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
    // 预检意见加前缀留痕(与 test_hx_audit 口径一致,后端按传入 opinion 落库)
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
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px 8px;
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
