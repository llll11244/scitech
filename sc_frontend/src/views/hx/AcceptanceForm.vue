<template>
  <div class="wizard">
    <div class="split">
      <!-- 左:表单区 -->
      <el-card shadow="never" class="form-card">
        <template #header>项目验收申请</template>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
          <el-form-item label="选择项目" prop="projectId">
            <el-select v-model="form.projectId" placeholder="仅已确认(立项)的项目可发起验收" class="w-full"
              :disabled="!!form.flowId" @change="onProjectChange">
              <el-option v-for="p in acceptableProjects" :key="p.projectId" :label="p.name" :value="p.projectId" />
            </el-select>
            <div class="tip">每项目至多一张验收单;成员与分工自登记数据回显,请补录比例与金额。</div>
          </el-form-item>

          <el-form-item label="使用经费(元)" prop="usedAmount">
            <el-input-number v-model="form.usedAmount" :min="0" :precision="2" :controls="false"
              placeholder="实际使用经费,单位元" class="w-200" @change="schedule" />
          </el-form-item>
          <el-form-item label="是否按期完成" prop="isOnTime">
            <el-radio-group v-model="form.isOnTime" @change="schedule">
              <el-radio :value="1">按期完成</el-radio>
              <el-radio :value="0">延期完成</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="成果形式" prop="outcomeForms">
            <el-checkbox-group v-model="form.outcomeForms" @change="schedule">
              <el-checkbox v-for="o in dictOutcomeForm" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="完成情况总结" prop="acceptSummary">
            <div class="w-full">
              <el-input v-model="form.acceptSummary" type="textarea" :rows="6" maxlength="1000" show-word-limit
                placeholder="目标达成情况、关键工作成果、进度与经费执行、客户评价与效益等" @change="schedule" />
              <el-collapse class="mt-4">
                <el-collapse-item title="填写示例" name="ex">
                  <div class="hint-block">示例:目标达成情况——项目严格按照合同约定完成生产线智能化改造方案设计与实施,自动化率从原XX%提升至XX%,超过预期目标;关键工作成果——提交《改造方案》《测试报告》《操作手册》等文档共XX份;进度与经费执行——较原计划提前XX天完成验收,经费使用无超支;客户评价与效益——年产能提升约XXXX万元,通过ISO 9001认证,客户表达后续合作意向。</div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </el-form-item>

          <!-- 教师/校外成员:补录比例与金额 -->
          <el-divider content-position="left">成员分工比例与金额</el-divider>
          <el-table :data="teacherRows" size="small" border>
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column prop="unit" label="单位" min-width="130" show-overflow-tooltip />
            <el-table-column prop="title" label="职称" width="80" />
            <el-table-column prop="duty" label="登记分工" min-width="120" show-overflow-tooltip />
            <el-table-column label="比例(%)" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.ratio" :min="0" :max="100" :precision="2" :controls="false"
                  size="small" placeholder="百分比" class="w-100" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="金额(元)" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.amount" :min="0" :precision="2" :controls="false"
                  size="small" placeholder="分成金额" class="w-110" @change="schedule" />
              </template>
            </el-table-column>
          </el-table>

          <!-- 学生成员:可增删 -->
          <el-divider content-position="left">学生参与情况</el-divider>
          <el-table :data="studentRows" size="small" border>
            <el-table-column label="姓名" width="100">
              <template #default="{ row }">
                <el-input v-model="row.name" size="small" placeholder="姓名" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="学号" width="130">
              <template #default="{ row }">
                <el-input v-model="row.studentNo" size="small" placeholder="学号" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="专业" width="120">
              <template #default="{ row }">
                <el-input v-model="row.major" size="small" placeholder="专业" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="指导教师" width="120">
              <template #default="{ row }">
                <el-input v-model="row.mentor" size="small" placeholder="指导教师" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="参与的工作任务" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.duty" size="small" placeholder="具体参与什么工作" @change="schedule" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="studentRows.splice($index, 1); schedule()">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" class="mt" @click="addStudent">+ 添加学生</el-button>

          <el-divider content-position="left">验收报告正文</el-divider>
          <el-form-item label="报告正文">
            <div class="w-full">
              <el-input v-model="form.reportContent" type="textarea" :rows="10" maxlength="20000"
                placeholder="空行分段(连续两个回车)为一段,每段一个标题或正文块;单面打印,可另附图纸图片等材料" @change="schedule" />
              <el-collapse class="mt-4">
                <el-collapse-item title="写作参考大纲" name="outline">
                  <div class="hint-block pre">一、项目目标达成情况:(一)技术服务内容与目标完成度;(二)项目实施节点执行情况;(三)技术标准与合规性
二、研究过程总结:(一)项目实施流程;(二)协作机制与资源整合;(三)质量控制体系
三、关键工作成果:(一)核心技术成果;(二)知识产权与资产;(三)创新亮点
四、进度与经费执行:(一)进度管理;(二)经费使用情况
五、客户评价与效益:(一)甲方评价与验收结论;(二)经济效益;(三)社会效益——行业影响、人才培养、可持续发展
六、总结与展望:(一)项目亮点;(二)改进方向;(三)未来合作建议
七、结论</div>
                </el-collapse-item>
              </el-collapse>
              <div class="tip">注:验收报告签名处须手写。</div>
            </div>
          </el-form-item>
        </el-form>

        <div class="actions">
          <el-button :loading="saving" @click="save">保存草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmSubmit">
            {{ rejected ? '保存并重交' : '提交验收' }}
          </el-button>
        </div>
      </el-card>

      <!-- 右:分屏预览(双模板切换) -->
      <div class="preview-col">
        <div class="preview-toolbar">
          <el-radio-group v-model="tplType" size="small" @change="schedule(true)">
            <el-radio-button value="hx_acceptance">验收登记表</el-radio-button>
            <el-radio-button value="hx_report">验收报告</el-radio-button>
          </el-radio-group>
        </div>
        <el-alert type="warning" :closable="false"
          title="打印以下载的 Word 文件为准"
          description="验收登记表:一式四份,双面打印,控制在两页内。验收报告:多页单面打印,签名处须手写。" />
        <div class="page-count">共 {{ pages }} 页</div>
        <div v-loading="previewLoading" class="preview-box">
          <el-empty v-if="!form.projectId" description="选择项目后显示预览" :image-size="70" />
          <DocxPane v-else :blob="previewBlob" @rendered="pages = $event" />
        </div>
      </div>
    </div>

    <!-- 提交确认:全页预览弹窗 -->
    <el-dialog v-model="confirmVisible" title="提交前请核对(打印以下载的 Word 文件为准)" width="72%" top="3vh">
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  flowDetail, listDict, myProjects, previewDocx, resubmit, saveAcceptanceDraft, submitAcceptance
} from '../../api/hx'
import DocxPane from './DocxPane.vue'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const rejected = ref(false)   // 退回单(4)编辑:保存后走 resubmit 而非 submit

const acceptableProjects = ref([]) // 在研且尚无验收单
const dictOutcomeForm = ref([])
const tplType = ref('hx_acceptance') // 预览模板:验收登记表/验收报告

const form = reactive({
  flowId: null,
  projectId: route.query.projectId ? Number(route.query.projectId) : null,
  usedAmount: null,
  isOnTime: 1,
  outcomeForms: [],
  acceptSummary: '',
  reportContent: ''
})

/* 成员两视图:教师/校外(补 ratio+amount)/学生(增删) */
const teacherRows = ref([])
const studentRows = ref([])

function addStudent() {
  studentRows.value.push({ memberType: '学生', name: '', studentNo: '', major: '', mentor: '', duty: '' })
}

const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  usedAmount: [{ required: true, message: '请输入使用经费', trigger: 'change' }],
  isOnTime: [{ required: true, message: '请选择是否按期完成', trigger: 'change' }],
  outcomeForms: [{ required: true, type: 'array', message: '请勾选成果形式', trigger: 'change' }],
  acceptSummary: [{ required: true, message: '请填写完成情况总结', trigger: 'blur' }],
  reportContent: [{ required: true, message: '请填写验收报告正文', trigger: 'blur' }]
}

/** members 全量覆盖(后端口径):教师/校外 + 学生合并,姓名空行不入 */
function collectForm() {
  const members = []
  teacherRows.value
    .filter((m) => (m.name || '').trim())
    .forEach((m, i) => members.push({ ...m, name: m.name.trim(), sortNo: i + 1 }))
  studentRows.value
    .filter((m) => (m.name || '').trim())
    .forEach((m, i) => members.push({ ...m, name: m.name.trim(), sortNo: teacherRows.value.length + i + 1 }))
  return {
    flowId: form.flowId,
    projectId: form.projectId,
    usedAmount: form.usedAmount,
    isOnTime: form.isOnTime,
    outcomeForm: form.outcomeForms.join(','),
    acceptSummary: form.acceptSummary,
    reportContent: form.reportContent,
    members
  }
}

/* ---------- 分屏预览(800ms 防抖;需先选项目) ---------- */
const previewLoading = ref(false)
const previewBlob = ref(null)
const pages = ref(0)
let previewSeq = 0
let timer = null

function schedule(immediate = false) {
  clearTimeout(timer)
  timer = setTimeout(doPreview, immediate ? 0 : 800)
}

async function doPreview() {
  if (!form.projectId) return
  const cur = ++previewSeq
  previewLoading.value = true
  try {
    const blob = await previewDocx('acceptance', collectForm(), tplType.value)
    if (cur !== previewSeq) return
    previewBlob.value = blob
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    if (cur === previewSeq) previewLoading.value = false
  }
}

function onProjectChange() {
  previewBlob.value = null
  pages.value = 0
  loadProjectMembers()
  schedule()
}

/** 从登记流程单详情回显成员(教师/校外、学生分表) */
async function loadProjectMembers() {
  if (!form.projectId) return
  const row = (await myProjects() || []).find((p) => p.projectId === form.projectId)
  const regFlow = row?.flows?.find((f) => f.flowType === 'register')
  if (!regFlow) {
    teacherRows.value = []
    studentRows.value = []
    return
  }
  const d = await flowDetail(regFlow.flowId)
  fillMembers(d.members || [])
}

function fillMembers(members) {
  teacherRows.value = members
    .filter((m) => m.memberType !== '学生')
    .map((m) => ({
      memberType: m.memberType || '教师', name: m.name || '', unit: m.unit || '',
      title: m.title || '', major: m.major || '', duty: m.duty || '',
      ratio: m.ratio, amount: m.amount
    }))
  studentRows.value = members
    .filter((m) => m.memberType === '学生')
    .map((m) => ({
      memberType: '学生', name: m.name || '', studentNo: m.studentNo || '',
      major: m.major || '', mentor: m.mentor || '', duty: m.duty || ''
    }))
}

/* ---------- 草稿保存 / 提交 ---------- */
const saving = ref(false)
const submitting = ref(false)
const confirmVisible = ref(false)
const confirmBlob = ref(null)

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const fid = await saveAcceptanceDraft(collectForm())
    form.flowId = fid
    ElMessage.success('草稿已保存')
    schedule()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function confirmSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const fid = await saveAcceptanceDraft(collectForm())
    form.flowId = fid
    confirmBlob.value = await previewDocx('acceptance', collectForm(), tplType.value)
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
      await saveAcceptanceDraft(collectForm())
      await resubmit(form.flowId)
      ElMessage.success('已重交,请下载待签 Word(两张)重新打印盖章')
    } else {
      await submitAcceptance(form.flowId)
      ElMessage.success('验收单已提交,请下载待签 Word(验收登记表+验收报告)打印盖章')
    }
    router.push(`/hx/flow/${form.flowId}`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

/* ---------- 草稿回填(?flowId=继续填报/退回单修改) ---------- */
async function loadDraft(flowId) {
  const d = await flowDetail(flowId)
  if (d.flow.status !== 0 && d.flow.status !== 4) {
    router.replace(`/hx/flow/${flowId}`)
    return
  }
  rejected.value = d.flow.status === 4
  form.flowId = d.flow.id
  form.projectId = d.flow.projectId
  form.usedAmount = d.flow.usedAmount
  form.isOnTime = d.flow.isOnTime ?? 1
  form.outcomeForms = (d.flow.outcomeForm || '').split(',').filter(Boolean)
  form.acceptSummary = d.flow.acceptSummary || ''
  form.reportContent = d.flow.reportContent || ''
  fillMembers(d.members || [])
  schedule()
}

onMounted(async () => {
  const [rows, of] = await Promise.all([myProjects(), listDict('hx_outcome_form')])
  acceptableProjects.value = (rows || []).filter(
    (p) => p.auditStatus === 2 && !p.flows?.some((f) => f.flowType === 'acceptance')
  )
  dictOutcomeForm.value = of
  if (route.query.flowId) {
    await loadDraft(Number(route.query.flowId))
  } else if (form.projectId) {
    await loadProjectMembers()
    schedule()
  }
})
</script>

<script>
export default { name: 'AcceptanceForm' }
</script>

<style scoped>
.wizard {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
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

.preview-toolbar {
  margin-bottom: 8px;
  text-align: center;
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

.w-full {
  width: 100%;
}

.w-200 {
  width: 200px;
}

.w-110 {
  width: 110px;
}

.w-100 {
  width: 100px;
}

.mt {
  margin-top: 10px;
}

.mt-4 {
  margin-top: 4px;
}

.tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
}

.hint-block {
  font-size: 13px;
  color: #909399;
  line-height: 1.8;
}

.hint-block.pre {
  white-space: pre-line;
}
</style>
