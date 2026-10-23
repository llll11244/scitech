<template>
  <div class="user-manage">
    <el-card shadow="never">
      <template #header>
        <div class="bar">
          <span>用户管理(建号/停启/重置密码)</span>
          <div class="ops">
            <el-input v-model="q" placeholder="用户名/姓名搜索" clearable class="w-200" @change="load" />
            <el-button type="primary" @click="openCreate">建号</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="username" label="用户名(工号)" width="140" />
        <el-table-column prop="realName" label="姓名" width="120">
          <template #default="{ row }">{{ row.realName || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="ROLE_TAG[row.role]?.type || 'info'" size="small">
              {{ ROLE_TAG[row.role]?.label || row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="院部" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column label="最近登录" width="150">
          <template #default="{ row }">{{ fmtTime(row.lastLogin) || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" :type="row.status === 1 ? 'warning' : 'success'"
              :disabled="row.username === userStore.userInfo?.username" @click="toggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="primary" size="small" @click="openReset(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
        layout="total, prev, pager, next" class="pager" @current-change="load" />
    </el-card>

    <!-- 建号 -->
    <el-dialog v-model="createVisible" title="建号" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="用户名(工号)" prop="username">
          <el-input v-model="createForm.username" placeholder="登录名,一般用工号" maxlength="64" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="createForm.realName" maxlength="64" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role" class="w-full" placeholder="选择角色">
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
            <el-option label="只读访客" value="viewer" />
          </el-select>
        </el-form-item>
        <el-form-item label="院部" prop="deptId">
          <el-select v-model="createForm.deptId" class="w-full" clearable placeholder="选择院部(教师建号建议必选)">
            <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职称" prop="title">
          <el-select v-model="createForm.title" class="w-full" clearable filterable allow-create
            placeholder="选择或输入(工号反显/表单带出)">
            <el-option v-for="t in titles" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="createForm.phone" maxlength="20" placeholder="登记表联系电话默认带出" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="8~32 位,含字母与数字" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="createForm.confirm" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">建号</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" :title="`重置密码-${resetTarget?.username || ''}`" width="440px">
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="100px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password placeholder="8~32 位,含字母与数字" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="重置后请线下告知新密码;该用户已登录会话不受影响(停用才即时踢出)。" />
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReset">重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<!--
  最小用户管理(计划裁定:建号/停启/重置密码,无删):变更负责人「须已有账号」依赖现场建号。
  停用即时生效(AuthInterceptor 每 uid 查库,决策#35);后端防线:不可停用自己/密码强度。
-->
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, listUsers, resetUserPassword, setUserStatus } from '../../api/system'
import { listDepts, listDict } from '../../api/hx'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const q = ref('')

const ROLE_TAG = {
  admin: { label: '管理员', type: 'danger' },
  teacher: { label: '教师', type: 'primary' },
  viewer: { label: '只读访客', type: 'info' }
}

const fmtTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

async function load() {
  loading.value = true
  try {
    const data = (await listUsers({ page: page.value, size, q: q.value || undefined })) || {}
    rows.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

/* ---------- 建号 ---------- */
const createVisible = ref(false)
const depts = ref([])
const titles = ref([])

async function loadMeta() {
  try {
    depts.value = await listDepts()
    titles.value = (await listDict('title')).map((x) => x.label)
  } catch (e) { /* 拦截器已提示 */ }
}

const createFormRef = ref()
const createForm = reactive({ username: '', realName: '', role: 'teacher', password: '', confirm: '', deptId: null, title: '', phone: '' })
const createRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    {
      validator: (r, v, cb) => {
        if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(v || '')) cb(new Error('8~32 位且同时包含字母与数字'))
        else cb()
      },
      trigger: 'blur'
    }
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (r, v, cb) => {
        if (v !== createForm.password) cb(new Error('两次输入不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  createForm.username = ''
  createForm.realName = ''
  createForm.role = 'teacher'
  createForm.password = ''
  createForm.confirm = ''
  createVisible.value = true
}

async function saveCreate() {
  await createFormRef.value.validate()
  saving.value = true
  try {
    await createUser({
      username: createForm.username.trim(),
      realName: createForm.realName.trim(),
      role: createForm.role,
      password: createForm.password
    })
    ElMessage.success('建号成功,请线下告知初始密码')
    createVisible.value = false
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ---------- 停启/重置 ---------- */
async function toggle(row) {
  if (row.status === 1) {
    try {
      await ElMessageBox.confirm(
        `停用「${row.username}」?该用户即刻起所有请求失效(含已登录会话)。`, '提示', { type: 'warning' })
    } catch { return }
  }
  try {
    await setUserStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(row.status === 1 ? '已停用' : '已启用')
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}

const resetVisible = ref(false)
const resetTarget = ref(null)
const resetFormRef = ref()
const resetForm = reactive({ password: '' })
const resetRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (r, v, cb) => {
        if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(v || '')) cb(new Error('8~32 位且同时包含字母与数字'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

function openReset(row) {
  resetTarget.value = row
  resetForm.password = ''
  resetVisible.value = true
}

async function saveReset() {
  await resetFormRef.value.validate()
  saving.value = true
  try {
    await resetUserPassword(resetTarget.value.id, resetForm.password)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

onMounted(() => { load(); loadMeta() })
</script>

<script>
export default { name: 'UserManage' }
</script>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ops {
  display: flex;
  gap: 10px;
}

.w-200 {
  width: 200px;
}

.w-full {
  width: 100%;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
