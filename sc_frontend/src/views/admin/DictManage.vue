<template>
  <div class="dict-manage">
    <el-card shadow="never">
      <template #header>
        <div class="bar">
          <span>数据字典管理</span>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" class="mb"
        title="内置取值(is_system)不可删除、显示名不可改,仅可停用/启用/调序;模板联动字典增删取值须同步更新 PDF 模板并重新上传。" />

      <el-tabs v-model="curType" @tab-change="load">
        <el-tab-pane v-for="t in types" :key="t.type" :name="t.type">
          <template #label>
            <span>{{ t.label }}</span>
            <el-tag v-if="t.linked" size="small" type="warning" effect="plain" class="ml-4">模板联动</el-tag>
          </template>
        </el-tab-pane>
      </el-tabs>

      <div class="toolbar">
        <el-button type="primary" size="small" @click="openAdd">新增取值</el-button>
        <span class="hint">当前类型:{{ curMeta.label }}{{ curMeta.linked ? '(模板联动)' : '' }}</span>
      </div>

      <el-table :data="rows" v-loading="loading" size="default">
        <el-table-column prop="dictValue" label="取值" width="160" />
        <el-table-column prop="dictLabel" label="显示名" min-width="160" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="内置" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isSystem === 1" size="small" type="info">内置</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" :disabled="row.isSystem === 1"
              @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 -->
    <el-dialog v-model="addVisible" title="新增字典取值" width="440px">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="90px">
        <el-form-item label="取值" prop="value">
          <el-input v-model="addForm.value" placeholder="存库值(不可改)" />
        </el-form-item>
        <el-form-item label="显示名" prop="label">
          <el-input v-model="addForm.label" placeholder="下拉框显示" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="addForm.sortOrder" :min="1" :max="999" :controls="false" class="w-120" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAdd">新增</el-button>
      </template>
    </el-dialog>

    <!-- 编辑(label/sortOrder/status;内置项 label 锁定) -->
    <el-dialog v-model="editVisible" title="编辑字典项" width="440px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="取值">
          <el-input :model-value="editForm.value" disabled />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="editForm.label" :disabled="editForm.isSystem === 1"
            :placeholder="editForm.isSystem === 1 ? '内置取值显示名不可改' : ''" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="1" :max="999" :controls="false" class="w-120" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0"
            active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<!--
  字典管理(spec §7.2,补阶段2遗留):类型 tab(横向 7 类,5 类带「模板联动」标记)+
  取值增删改停;内置保护(is_system=1:label 不可改/不可删,前端锁+后端拒双防线);
  联动字典增/删响应带 remind→强提醒弹窗(取值印在模板勾选组/科目行,须同步改模板重传)。
-->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addDict, deleteDict, manageDicts, updateDict } from '../../api/system'

/* 种子 8 类(顺序=tab 顺序);linked=模板联动(增删强提醒);2026-10-19 决策#32 前缀对齐+新增职称 */
const KNOWN_TYPES = [
  { type: 'hx_project_type', label: '横向项目类型', linked: true },
  { type: 'hx_client_nature', label: '委托单位性质', linked: true },
  { type: 'hx_outcome_form', label: '成果形式', linked: true },
  { type: 'hx_change_type', label: '变更类型', linked: true },
  { type: 'hx_budget_subject', label: '横向预算科目', linked: true },
  { type: 'hx_member_type', label: '成员类型', linked: false },
  { type: 'title', label: '职称', linked: false },
  { type: 'hx_scan_doc_type', label: '盖章扫描件类型', linked: false }
]

const loading = ref(false)
const rows = ref([])
const extraTypes = ref([])   // 库中超出种子清单的类型(动态补 tab)
const curType = ref(KNOWN_TYPES[0].type)

const types = computed(() => [...KNOWN_TYPES, ...extraTypes.value])
const curMeta = computed(() => types.value.find((t) => t.type === curType.value) || { label: curType.value })

async function load() {
  loading.value = true
  try {
    const all = (await manageDicts()) || []
    // 全量拉取后按当前类型过滤展示;顺带发现新增类型(补 tab)
    rows.value = all.filter((r) => r.dictType === curType.value)
    const known = new Set(KNOWN_TYPES.map((t) => t.type))
    extraTypes.value = [...new Set(all.map((r) => r.dictType).filter((t) => !known.has(t)))]
      .map((t) => ({ type: t, label: t, linked: false }))
  } finally {
    loading.value = false
  }
}

/** 联动强提醒弹窗(后端 remind 文案权威,前端只展示) */
function remindPopup(remind) {
  if (!remind) return
  ElMessageBox.alert(remind, '模板同步提醒', {
    type: 'warning',
    confirmButtonText: '知道了',
    confirmButtonClass: 'el-button--warning'
  }).catch(() => {})
}

/* ---------- 新增 ---------- */
const addVisible = ref(false)
const saving = ref(false)
const addFormRef = ref()
const addForm = reactive({ value: '', label: '', sortOrder: 99 })
const addRules = {
  value: [{ required: true, message: '请输入取值', trigger: 'blur' }],
  label: [{ required: true, message: '请输入显示名', trigger: 'blur' }]
}

function openAdd() {
  addForm.value = ''
  addForm.label = ''
  addForm.sortOrder = 99
  addVisible.value = true
}

async function saveAdd() {
  await addFormRef.value.validate()
  saving.value = true
  try {
    const data = await addDict(curType.value, addForm.value.trim(), addForm.label.trim())
    ElMessage.success('已新增')
    addVisible.value = false
    if (addForm.sortOrder !== 99) {
      const row = ((await manageDicts(curType.value)) || []).find((r) => r.dictValue === addForm.value.trim())
      if (row) await updateDict(row.id, { sortOrder: addForm.sortOrder })
    }
    remindPopup(data?.remind)
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ---------- 编辑/停启用/删除 ---------- */
const editVisible = ref(false)
const editForm = reactive({ id: null, value: '', label: '', sortOrder: 99, status: 1, isSystem: 0 })

function openEdit(row) {
  editForm.id = row.id
  editForm.value = row.dictValue
  editForm.label = row.dictLabel
  editForm.sortOrder = row.sortOrder
  editForm.status = row.status
  editForm.isSystem = row.isSystem
  editVisible.value = true
}

async function saveEdit() {
  saving.value = true
  try {
    await updateDict(editForm.id, {
      label: editForm.isSystem === 1 ? undefined : editForm.label,
      sortOrder: editForm.sortOrder,
      status: editForm.status
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  const to = row.status === 1 ? 0 : 1
  try {
    await updateDict(row.id, { status: to })
    ElMessage.success(to === 1 ? '已启用' : '已停用(下拉框不再出现)')
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除取值「${row.dictLabel}」?已有数据引用该值的不受影响。`, '提示',
      { type: 'warning' })
  } catch { return }
  try {
    const data = await deleteDict(row.id)
    ElMessage.success('已删除')
    remindPopup(data?.remind)
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<script>
export default { name: 'DictManage' }
</script>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mb {
  margin-bottom: 12px;
}

.ml-4 {
  margin-left: 4px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.hint {
  font-size: 12px;
  color: #909399;
}

.w-120 {
  width: 120px;
}
</style>
