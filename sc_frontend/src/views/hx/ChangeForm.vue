<template>
  <div class="wizard">
    <div class="split">
      <!-- 左:表单区 -->
      <el-card shadow="never" class="form-card">
        <template #header>项目变更申请</template>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
          <el-form-item label="选择项目" prop="projectId">
            <el-select v-model="form.projectId" placeholder="仅已确认(立项)的项目可发起变更" class="w-full"
              :disabled="!!form.flowId" @change="onProjectChange">
              <el-option v-for="p in confirmedProjects" :key="p.projectId" :label="p.name" :value="p.projectId" />
            </el-select>
          </el-form-item>

          <el-form-item label="变更类型" prop="changeTypes">
            <el-checkbox-group v-model="form.changeTypes" @change="schedule">
              <el-checkbox v-for="o in dictChangeType" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <!-- 联动字段:勾哪种类型出哪个输入 -->
          <el-form-item v-if="hasType('项目经费')" label="变更后经费(元)" prop="newAmount">
            <el-input-number v-model="form.newAmount" :min="0" :precision="2" :controls="false"
              placeholder="变更后合同总经费" class="w-200" @change="schedule" />
          </el-form-item>
          <el-form-item v-if="hasType('执行周期')" label="变更后结束时间" prop="newEndDate">
            <el-date-picker v-model="form.newEndDate" type="date" value-format="YYYY-MM-DD"
              placeholder="变更后结束日期" class="w-160" @change="schedule" />
          </el-form-item>
          <el-form-item v-if="hasType('项目负责人')" label="变更后负责人" prop="newLeaderId">
            <el-select v-model="form.newLeaderId" filterable remote reserve-keyword placeholder="输入姓名/用户名搜索"
              :remote-method="searchLeader" :loading="leaderLoading" class="w-280" @change="schedule">
              <el-option v-for="u in leaders" :key="u.id" :label="`${u.name}(${u.username})`" :value="u.id" />
            </el-select>
            <div class="tip">变更人员的,请变更人员亲笔签名,各单位或部门盖章并由负责人签字。</div>
          </el-form-item>

          <el-form-item label="变更内容" prop="changeContent">
            <div class="w-full">
              <el-input v-model="form.changeContent" type="textarea" :rows="5" maxlength="500" show-word-limit
                placeholder="对合同约定的关键内容作何调整(原约定 → 变更为)" @change="schedule" />
              <el-collapse class="mt-4">
                <el-collapse-item title="填写示例" name="ex">
                  <div class="hint-block">场景:原约定专利申请权归企业所有,后经协商调整为「双方共同申请专利,企业拥有独占实施权」。变更内容:专利申请权从「企业单独所有」变更为「双方共有」,实施权归属不变。</div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </el-form-item>
          <el-form-item label="变更原因" prop="changeReason">
            <el-input v-model="form.changeReason" type="textarea" :rows="3" maxlength="300" show-word-limit
              placeholder="申请变更的原因" @change="schedule" />
          </el-form-item>
        </el-form>

        <div class="actions">
          <el-button :loading="saving" @click="save">保存草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmSubmit">
            {{ rejected ? '保存并重交' : '提交变更' }}
          </el-button>
        </div>
      </el-card>

      <!-- 右:分屏预览 -->
      <div class="preview-col">
        <el-alert type="warning" :closable="false"
          title="打印以下载的 Word 文件为准"
          description="注:变更项目负责人、研究人员、合同请填写此表;其中变更人员的须亲笔签名。本表一式三份,双面打印,控制在两页内(分页打印需委托方盖骑缝章)。" />
        <div class="page-count">共 {{ pages }} 页</div>
        <div v-loading="previewLoading" class="preview-box">
          <el-empty v-if="!form.projectId" description="选择项目后显示变更表预览" :image-size="70" />
          <DocxPane v-else :blob="previewBlob" @rendered="pages = $event" />
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  flowDetail, listDict, myProjects, previewDocx, resubmit, saveChangeDraft,
  searchLeaders, submitChange
} from '../../api/hx'
import DocxPane from './DocxPane.vue'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const rejected = ref(false)   // 退回单(4)编辑:保存后走 resubmit 而非 submit

const confirmedProjects = ref([]) // 在研(audit_status=2)项目
const dictChangeType = ref([])
const leaders = ref([])
const leaderLoading = ref(false)

const form = reactive({
  flowId: null,
  projectId: route.query.projectId ? Number(route.query.projectId) : null,
  changeTypes: [],
  changeContent: '',
  changeReason: '',
  newAmount: null,
  newEndDate: '',
  newLeaderId: null
})

const hasType = (t) => form.changeTypes.includes(t)

const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  changeTypes: [
    { required: true, type: 'array', message: '请勾选变更类型', trigger: 'change' },
    {
      validator: (r, v, cb) => {
        if (hasType('项目经费') && (form.newAmount === null || form.newAmount === undefined)) cb(new Error('勾选「项目经费」须填写变更后经费'))
        else if (hasType('执行周期') && !form.newEndDate) cb(new Error('勾选「执行周期」须选择变更后结束时间'))
        else if (hasType('项目负责人') && !form.newLeaderId) cb(new Error('勾选「项目负责人」须选择变更后负责人'))
        else cb()
      },
      trigger: 'change'
    }
  ],
  changeContent: [{ required: true, message: '请填写变更内容', trigger: 'blur' }],
  changeReason: [{ required: true, message: '请填写变更原因', trigger: 'blur' }]
}

/** 组装 payload:changeType 逗号串(与后端字典口径一致) */
function collectForm() {
  return {
    flowId: form.flowId,
    projectId: form.projectId,
    changeType: form.changeTypes.join(','),
    changeContent: form.changeContent,
    changeReason: form.changeReason,
    newAmount: hasType('项目经费') ? form.newAmount : null,
    newEndDate: hasType('执行周期') ? form.newEndDate : null,
    newLeaderId: hasType('项目负责人') ? form.newLeaderId : null
  }
}

/* ---------- 分屏预览(800ms 防抖;需先选项目) ---------- */
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
  if (!form.projectId) return
  const cur = ++previewSeq
  previewLoading.value = true
  try {
    const blob = await previewDocx('change', collectForm())
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
  schedule()
}

/* ---------- 负责人远程搜索 ---------- */
async function searchLeader(q) {
  if (!q) { leaders.value = []; return }
  leaderLoading.value = true
  try {
    leaders.value = (await searchLeaders(q)) || []
  } catch (e) {
    leaders.value = []
  } finally {
    leaderLoading.value = false
  }
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
    const fid = await saveChangeDraft(collectForm())
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
    const fid = await saveChangeDraft(collectForm())
    form.flowId = fid
    confirmBlob.value = await previewDocx('change', collectForm())
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
      await saveChangeDraft(collectForm())
      await resubmit(form.flowId)
      ElMessage.success('已重交,请下载待签 Word 重新打印盖章')
    } else {
      await submitChange(form.flowId)
      ElMessage.success('变更单已提交,请下载待签 Word 打印盖章')
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
  form.changeTypes = (d.flow.changeType || '').split(',').filter(Boolean)
  form.changeContent = d.flow.changeContent || ''
  form.changeReason = d.flow.changeReason || ''
  form.newAmount = d.flow.newAmount
  form.newEndDate = normDate(d.flow.newEndDate)
  form.newLeaderId = d.flow.newLeaderId
  if (form.newLeaderId) {
    // 回显已选负责人:先拉一次搜索结果供 label 显示
    await searchLeader(String(form.newLeaderId))
    if (!leaders.value.some((u) => u.id === form.newLeaderId)) {
      leaders.value.unshift({ id: form.newLeaderId, username: 'ID:' + form.newLeaderId, name: '已选负责人 #' + form.newLeaderId })
    }
  }
  schedule()
}

/** LocalDate 序列化可能是 ISO 串或 [y,m,d] 数组,统一成 yyyy-MM-dd */
function normDate(v) {
  if (!v) return ''
  if (Array.isArray(v)) return v.slice(0, 3).map((x) => String(x).padStart(2, '0')).join('-')
  return String(v).slice(0, 10)
}

onMounted(async () => {
  const [rows, ct] = await Promise.all([myProjects(), listDict('hx_change_type')])
  confirmedProjects.value = (rows || []).filter((p) => p.auditStatus === 2)
  dictChangeType.value = ct
  if (route.query.flowId) {
    await loadDraft(Number(route.query.flowId))
  } else if (form.projectId) {
    schedule()
  }
})
</script>

<script>
export default { name: 'ChangeForm' }
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

.w-280 {
  width: 280px;
}

.w-200 {
  width: 200px;
}

.w-160 {
  width: 160px;
}

.mt-4 {
  margin-top: 4px;
}

.tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 2px;
}

.hint-block {
  font-size: 13px;
  color: #909399;
  line-height: 1.8;
}
</style>
