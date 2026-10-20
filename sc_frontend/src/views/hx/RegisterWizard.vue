<template>
  <div class="wizard">
    <!-- 退回单修改重交提示 -->
    <el-alert v-if="rejected" type="error" :closable="false" class="mb"
      title="该登记单已被科技处退回,修改完成后点「保存并重交」;重交后原盖章件作废,需重新盖章回扫(意见见流程详情时间线)。" />

    <!-- 步骤条 -->
    <el-steps :active="step" simple finish-status="success" class="mb">
      <el-step title="基本信息" />
      <el-step title="成员及分工" />
      <el-step title="预算与简介" />
    </el-steps>

    <div class="split">
      <!-- 左:表单区 -->
      <el-card shadow="never" class="form-card">
        <!-- 第 1 步:基本信息 -->
        <div v-show="step === 0">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
            <el-form-item label="项目名称" prop="name">
              <el-input v-model="form.name" placeholder="按合同全称填写" maxlength="100" show-word-limit @change="schedule" />
            </el-form-item>
            <el-form-item label="项目负责人" prop="leaderName">
              <el-input v-model="form.leaderName" placeholder="负责人姓名" @change="schedule" />
            </el-form-item>
            <el-form-item label="所在院部" prop="deptId">
              <el-select v-model="form.deptId" placeholder="选择院部" class="w-full" @change="schedule">
                <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-divider content-position="left">合同信息</el-divider>
            <el-form-item label="项目类型" prop="hx.projectType">
              <el-radio-group v-model="form.hx.projectType" @change="schedule">
                <el-radio v-for="o in dictProjectType" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="委托单位" prop="hx.clientName">
              <el-input v-model="form.hx.clientName" placeholder="合同甲方全称" @change="schedule" />
            </el-form-item>
            <el-form-item label="委托单位性质" prop="hx.clientNature">
              <el-radio-group v-model="form.hx.clientNature" @change="schedule">
                <el-radio v-for="o in dictClientNature" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="合同经费(元)" prop="hx.contractAmount">
              <el-input-number v-model="form.hx.contractAmount" :min="0" :precision="2" :controls="false"
                placeholder="金额,单位元" class="w-200" @change="schedule" />
            </el-form-item>
            <el-form-item label="合同起止时间" prop="hx.startDate">
              <div class="range">
                <el-date-picker v-model="form.hx.startDate" type="date" value-format="YYYY-MM-DD"
                  placeholder="开始日期" class="w-160" @change="schedule" />
                <span class="sep">至</span>
                <el-form-item prop="hx.endDate" class="inline"><el-date-picker v-model="form.hx.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" class="w-160" @change="schedule" /></el-form-item>
              </div>
            </el-form-item>
            <el-form-item label="使用学校资产">
              <el-radio-group v-model="form.hx.useSchoolAsset" @change="schedule">
                <el-radio :value="0">否</el-radio>
                <el-radio :value="1">是</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="收取管理费">
              <el-radio-group v-model="form.hx.chargeMgmtFee" @change="schedule">
                <el-radio :value="0">否</el-radio>
                <el-radio :value="1">是</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.hx.chargeMgmtFee === 1" label="管理费金额(元)">
              <el-input-number :model-value="mgmtFeePreview" :min="0" :precision="2" :controls="false"
                disabled placeholder="自动换算" class="w-200" />
              <div class="tip-txt">比例由科技处统一设定(当前 {{ mgmtRate == null ? '—' : mgmtRate }}%),金额 = 合同经费 × 比例自动换算,不可自行修改。</div>
            </el-form-item>
            <el-form-item label="负责人邮箱">
              <el-input v-model="form.hx.leaderEmail" placeholder="用于接收通知(选填)" @change="schedule" />
            </el-form-item>
          </el-form>
        </div>

        <!-- 第 2 步:成员及分工 -->
        <div v-show="step === 1">
          <el-alert type="info" :closable="false" class="mb"
            title="成员行按顺序对应登记表固定行;姓名留空的行不会保存。学生成员的学号/指导教师信息在验收阶段补录。" />
          <el-table :data="members" size="small" border>
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-select v-model="row.memberType" size="small" @change="schedule">
                  <el-option v-for="o in dictMemberType" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="姓名" width="110">
              <template #default="{ row }">
                <el-input v-model="row.name" size="small" placeholder="姓名" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="单位" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.unit" size="small" placeholder="所在单位/学院" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="职称" width="110">
              <template #default="{ row }">
                <el-select v-model="row.title" size="small" filterable allow-create default-first-option
                           placeholder="选择或输入" @change="schedule">
                  <el-option v-for="o in dictTitle" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="从事专业" width="120">
              <template #default="{ row }">
                <el-input v-model="row.major" size="small" placeholder="专业" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="承担工作任务" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.duty" size="small" placeholder="具体分工" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeMember($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button class="mt" @click="addMember">+ 添加成员</el-button>
        </div>

        <!-- 第 3 步:预算与简介 -->
        <div v-show="step === 2">
          <el-form label-width="130px">
            <el-divider content-position="left">经费预算(元)</el-divider>
            <el-table :data="budgets" size="small" border>
              <el-table-column prop="subject" label="科目" width="140" />
              <el-table-column label="金额(元)">
                <template #default="{ row }">
                  <el-input-number v-model="row.amount" :min="0" :precision="2" :controls="false"
                    size="small" placeholder="留空=不列支" class="w-160" @change="schedule" />
                </template>
              </el-table-column>
            </el-table>
            <el-collapse class="mt">
              <el-collapse-item title="费用科目说明" name="1">
                <ul class="hint-list">
                  <li>请根据合同约定填写预算,若合同约定预算不设比例限制,则每项填写:「合同约定不设比例」。</li>
                  <li>业务费:与项目研究相关的直接支出,含设备费、材料费、测试化验加工费、燃料动力费、差旅费、会议费、国际合作与交流费、出版/文献/信息传播/知识产权事务费等,据实列支、不设比例限制。</li>
                  <li>劳务费:非校内在编人员的劳务、咨询、评审、验收、鉴定等开支(校内人员的费用属于绩效)。</li>
                  <li>绩效:用于奖励项目研究团队,由负责人按实际贡献自主确定,比例注意查看《横向科研项目及经费管理办法》。</li>
                  <li>业务接待费:项目执行过程中发生的接待费等;协作费应在合同中明确约定。</li>
                </ul>
              </el-collapse-item>
            </el-collapse>

            <el-divider content-position="left">合同扫描件(可选)</el-divider>
            <el-form-item label="合同扫描件">
              <div class="w-full">
                <el-upload :show-file-list="false" :auto-upload="false" accept=".pdf,.png,.jpg,.jpeg"
                  :on-change="onContractPicked">
                  <el-button size="small" :loading="contractUploading">
                    {{ contractFile ? '重新上传(覆盖旧件)' : '上传合同扫描件' }}
                  </el-button>
                </el-upload>
                <div v-if="contractFile" class="contract-row">
                  <span class="contract-name">{{ contractFile.fileName }}</span>
                  <span class="contract-time">{{ fmtTime(contractFile.createTime) }}</span>
                </div>
                <div class="tip-txt">扫描合同或盖章页均可,pdf/图片;不随本表盖章流程,科技处审核时调阅。</div>
              </div>
            </el-form-item>

            <el-divider content-position="left">项目简介与进度</el-divider>
            <el-form-item label="项目简介">
              <div class="w-full">
                <el-input v-model="form.hx.projectSummary" type="textarea" :rows="6" maxlength="800" show-word-limit
                  placeholder="项目背景、目标与内容、技术路线、预期成果(建议 200-300 字)" @change="schedule" />
                <div class="count">{{ (form.hx.projectSummary || '').length }} 字(建议 200-300 字)</div>
                <el-collapse>
                  <el-collapse-item title="填写示例" name="ex1">
                    <div class="hint-block">按「项目背景 → 项目目标和内容 → 技术路线 → 预期成果」四层展开:阐述甲方需求与立项必要性;明确技术服务成果与交付物(技术报告、解决方案、专利等)及服务周期;说明技术方法与工作流程,突出可行性与合作模式;总结对客户的价值提升并给出量化指标。</div>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </el-form-item>
            <el-form-item label="项目计划进度">
              <div class="w-full">
                <el-input v-model="form.hx.progressPlan" type="textarea" :rows="6" maxlength="800" show-word-limit
                  placeholder="按时间段列出各阶段工作安排(建议 200-300 字)" @change="schedule" />
                <div class="count">{{ (form.hx.progressPlan || '').length }} 字(建议 200-300 字)</div>
                <el-collapse>
                  <el-collapse-item title="填写示例" name="ex2">
                    <div class="hint-block">示例:2025年X月-2025年X月:实地调研现状,明确需求,制定初步技术路线;2025年X月-X月:设计方案并开展技术测试与评审;X月-X月:实施与系统集成、联调测试、操作培训;X月-X月:项目验收,提交报告,资料归档与总结。</div>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </el-form-item>
          </el-form>
        </div>

        <!-- 底部操作 -->
        <div class="actions">
          <el-button v-if="step > 0" @click="step--">上一步</el-button>
          <el-button v-if="step < 2" type="primary" @click="step++">下一步</el-button>
          <el-button v-if="step === 2" :loading="saving" @click="save">保存草稿</el-button>
          <el-button v-if="step === 2" type="primary" :loading="submitting" @click="confirmSubmit">
            {{ rejected ? '保存并重交' : '提交登记' }}
          </el-button>
        </div>
      </el-card>

      <!-- 右:分屏预览 -->
      <div class="preview-col">
        <el-alert type="warning" :closable="false"
          title="打印以下载的 Word 文件为准"
          description="注:本表一式四份(科技处、计财处、所在单位、项目负责人各一份),双面打印,控制在两页内。" />
        <div class="page-count">共 {{ pages }} 页</div>
        <div v-loading="previewLoading" class="preview-box">
          <DocxPane :blob="previewBlob" @rendered="pages = $event" />
        </div>
      </div>
    </div>

    <!-- 提交确认:全页预览弹窗 -->
    <el-dialog v-model="confirmVisible" title="提交前请核对全表(打印以下载的 Word 文件为准)" width="72%" top="3vh">
      <div class="confirm-box">
        <DocxPane :blob="confirmBlob" />
      </div>
      <template #footer>
        <el-button @click="confirmVisible = false">返回修改</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  flowDetail, getConfig, listDepts, listDict, previewDocx, resubmit, saveRegisterDraft,
  submitRegister, uploadContract
} from '../../api/hx'
import { useUserStore } from '../../stores/user'
import DocxPane from './DocxPane.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const step = ref(0)
const formRef = ref()
const rejected = ref(false)   // 退回单(4)编辑:保存后走 resubmit 而非 submit

/* ---------- 字典与院部 ---------- */
const depts = ref([])
const dictProjectType = ref([])
const dictClientNature = ref([])
const dictMemberType = ref([])
const dictTitle = ref([])

/* ---------- 表单 ---------- */
const form = reactive({
  flowId: null,
  name: '',
  leaderName: '',
  deptId: null,
  hx: {
    projectType: '', clientName: '', clientNature: '', contractAmount: null,
    startDate: '', endDate: '', useSchoolAsset: 0, chargeMgmtFee: 0,
    leaderEmail: '', projectSummary: '', progressPlan: ''
  }
})

/* ---------- 管理费比例(科技处配置,教师只读换算) ---------- */
const mgmtRate = ref(null)   // %(sys_config.hx_mgmt_fee_rate;null=未取到)
const mgmtFeePreview = computed(() => {
  if (form.hx.chargeMgmtFee !== 1 || form.hx.contractAmount == null || mgmtRate.value == null) {
    return null
  }
  return +(form.hx.contractAmount * mgmtRate.value / 100).toFixed(2)   // 展示口径;落库以后端换算为准
})
const members = ref([newMember()])
const budgets = ref([])

function newMember() {
  return { memberType: '教师', name: '', unit: '', title: '', major: '', duty: '' }
}
function addMember() {
  members.value.push(newMember())
}
function removeMember(i) {
  members.value.splice(i, 1)
}

/* 必填口径与后端 validateRegister 一致 */
const rules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  leaderName: [{ required: true, message: '请输入负责人', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择院部', trigger: 'change' }],
  'hx.projectType': [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  'hx.clientName': [{ required: true, message: '请输入委托单位', trigger: 'blur' }],
  'hx.clientNature': [{ required: true, message: '请选择委托单位性质', trigger: 'change' }],
  'hx.contractAmount': [{ required: true, message: '请输入合同经费', trigger: 'change' }],
  'hx.startDate': [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  'hx.endDate': [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

/** 组装提交/预览数据(空行成员、留空预算不入 payload) */
function collectForm() {
  return {
    flowId: form.flowId,
    name: form.name,
    leaderName: form.leaderName,
    deptId: form.deptId,
    hx: { ...form.hx },
    members: members.value
      .filter((m) => (m.name || '').trim())
      .map((m, i) => ({ ...m, name: m.name.trim(), sortNo: i + 1 })),
    budgets: budgets.value
      .filter((b) => b.amount !== null && b.amount !== undefined)
      .map((b) => ({ subject: b.subject, amount: b.amount }))
  }
}

/* ---------- 分屏预览(800ms 防抖) ---------- */
const previewLoading = ref(false)
const previewBlob = ref(null)
const pages = ref(0)
let previewSeq = 0
let timer = null

function schedule() {
  clearTimeout(timer)
  timer = setTimeout(doPreview, 800)
}

async function doPreview() {
  const cur = ++previewSeq
  previewLoading.value = true
  try {
    const blob = await previewDocx('register', collectForm())
    if (cur !== previewSeq) return
    previewBlob.value = blob
  } catch (e) {
    /* 拦截器已提示;预览失败不阻断填报 */
  } finally {
    if (cur === previewSeq) previewLoading.value = false
  }
}

// 切步/成员预算变化也会触发预览刷新
watch([step, members, budgets], schedule, { deep: true })

/* ---------- 草稿保存 ---------- */
const saving = ref(false)

async function save() {
  await validateAll()
  saving.value = true
  try {
    const fid = await saveRegisterDraft(collectForm())
    form.flowId = fid
    if (String(route.params.flowId || '') !== String(fid)) {
      router.replace(`/hx/register/${fid}`)
    }
    ElMessage.success('草稿已保存')
    schedule()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

function validateAll() {
  return formRef.value.validate().catch(() => {
    step.value = 0 // 必填集中在第 1 步,校验失败回到对应步
    return Promise.reject(new Error('必填项未完成'))
  })
}

/* ---------- 提交(全页预览确认) ---------- */
const confirmVisible = ref(false)
const confirmBlob = ref(null)
const submitting = ref(false)

async function confirmSubmit() {
  await validateAll()
  submitting.value = true
  try {
    const fid = await saveRegisterDraft(collectForm()) // 先保存最新数据
    form.flowId = fid
    confirmBlob.value = await previewDocx('register', collectForm())
    confirmVisible.value = true
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function doSubmit() {
  submitting.value = true
  try {
    if (rejected.value) {
      // 退回单修改重交:先存最新数据,再 4→1 重交(重渲待签,旧件作废)
      await saveRegisterDraft(collectForm())
      await resubmit(form.flowId)
      ElMessage.success('已重交,请下载待签 Word 重新打印盖章')
    } else {
      await submitRegister(form.flowId)
      ElMessage.success('登记已提交,请下载待签 Word 打印盖章')
    }
    router.push(`/hx/flow/${form.flowId}`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

/* ---------- 合同扫描件(可选,任何状态可传,重传覆盖) ---------- */
const contractFile = ref(null)
const contractUploading = ref(false)

const fmtTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

/** 详情附件中本流程单生效的合同(hx_apply/合同/apply 目录段) */
function pickContract(attachments, fid) {
  return (attachments || []).find((a) => a.bizType === 'hx_apply' && a.docType === '合同'
    && (a.filePath || '').includes(`/apply/${fid}/`)) || null
}

async function onContractPicked(file) {
  const raw = file.raw
  if (!raw) return
  contractUploading.value = true
  try {
    if (!form.flowId) {
      // 尚未存过草稿:先落库拿 flowId 再传(合同上传挂在流程单上)
      form.flowId = await saveRegisterDraft(collectForm())
      if (String(route.params.flowId || '') !== String(form.flowId)) {
        router.replace(`/hx/register/${form.flowId}`)
      }
    }
    await uploadContract(form.flowId, raw)
    const d = await flowDetail(form.flowId)
    contractFile.value = pickContract(d.attachments, form.flowId)
    ElMessage.success('合同扫描件已上传')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    contractUploading.value = false
  }
}

/* ---------- 草稿回填 ---------- */
async function loadDraft(flowId) {
  const d = await flowDetail(flowId)
  if (d.flow.status !== 0 && d.flow.status !== 4) {
    // 非草稿/退回单不可编辑,转流程详情查看
    router.replace(`/hx/flow/${flowId}`)
    return
  }
  rejected.value = d.flow.status === 4
  form.flowId = d.flow.id
  contractFile.value = pickContract(d.attachments, d.flow.id)
  form.name = d.project.name
  form.leaderName = d.project.leaderName
  form.deptId = d.project.deptId
  const hx = d.hx || {}
  form.hx.projectType = hx.projectType || ''
  form.hx.clientName = hx.clientName || ''
  form.hx.clientNature = hx.clientNature || ''
  form.hx.contractAmount = hx.contractAmount
  form.hx.startDate = hx.startDate ? String(hx.startDate).slice(0, 10) : ''
  form.hx.endDate = hx.endDate ? String(hx.endDate).slice(0, 10) : ''
  form.hx.useSchoolAsset = hx.useSchoolAsset ?? 0
  form.hx.chargeMgmtFee = hx.chargeMgmtFee ?? 0
  form.hx.leaderEmail = hx.leaderEmail || ''
  form.hx.projectSummary = hx.projectSummary || ''
  form.hx.progressPlan = hx.progressPlan || ''
  if (d.members?.length) {
    members.value = d.members.map((m) => ({
      memberType: m.memberType || '教师', name: m.name || '', unit: m.unit || '',
      title: m.title || '', major: m.major || '', duty: m.duty || ''
    }))
  }
  if (d.budgets?.length) {
    const bySubject = Object.fromEntries(d.budgets.map((b) => [b.subject, b.amount]))
    budgets.value.forEach((b) => { b.amount = bySubject[b.subject] ?? null })
  }
  schedule()
}

onMounted(async () => {
  form.leaderName = userStore.userInfo?.realName || ''
  getConfig('hx_mgmt_fee_rate').then((v) => { mgmtRate.value = v == null ? null : Number(v) })
  const [ds, pt, cn, mt, bs, tt] = await Promise.all([
    listDepts(),
    listDict('hx_project_type'),
    listDict('hx_client_nature'),
    listDict('hx_member_type'),
    listDict('hx_budget_subject'),
    listDict('title')
  ])
  depts.value = ds
  dictProjectType.value = pt
  dictClientNature.value = cn
  dictMemberType.value = mt
  dictTitle.value = tt
  budgets.value = bs.map((b) => ({ subject: b.label, amount: null }))
  if (route.params.flowId) {
    await loadDraft(Number(route.params.flowId))
  } else {
    schedule()
  }
})
</script>

<script>
export default { name: 'RegisterWizard' }
</script>

<style scoped>
.wizard {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
}

.mb {
  margin-bottom: 12px;
}

.split {
  display: flex;
  gap: 14px;
  flex: 1;
  min-height: 0;
}

.form-card {
  flex: 1.15;
  min-width: 0;
  overflow-y: auto;
}

.preview-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.page-count {
  margin: 8px 0 6px;
  font-size: 13px;
  color: #606266;
  text-align: center;
}

.preview-box {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px 8px;
  background: #f5f7fa;
}

.confirm-box {
  height: 68vh;
  overflow-y: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px 8px;
  background: #f5f7fa;
}

.actions {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.range {
  display: flex;
  align-items: center;
}

.inline {
  margin-bottom: 0;
}

.sep {
  margin: 0 8px;
}

.w-full {
  width: 100%;
}

.w-200 {
  width: 200px;
}

.w-160 {
  width: 160px;
}

.mt {
  margin-top: 10px;
}

.count {
  font-size: 12px;
  color: #909399;
  margin: 4px 0;
}

.hint-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}

.hint-block {
  font-size: 13px;
  color: #909399;
  line-height: 1.8;
}

.contract-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  font-size: 13px;
}

.contract-name {
  color: #303133;
}

.contract-time {
  color: #909399;
  font-size: 12px;
}

.tip-txt {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
