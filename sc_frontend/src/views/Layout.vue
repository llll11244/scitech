<template>
  <el-container class="layout">
    <!-- 侧边菜单(按角色显示) -->
    <el-aside width="220px" class="aside">
      <div class="logo">科研管理平台</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#1d2b3a"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <!-- 教师端(N3 起挂横向项目入口) -->
        <template v-if="userStore.isTeacher">
          <el-menu-item index="/notify">
            <el-icon><Bell /></el-icon>
            <span>消息通知</span>
            <el-badge v-if="unread > 0" :value="unread" :max="99" class="menu-badge" />
          </el-menu-item>
        </template>
        <!-- 科技处端(N4 起挂横向审批/字典/备份) -->
        <template v-else>
          <el-menu-item v-if="userStore.isAdmin" index="/notify">
            <el-icon><Bell /></el-icon>
            <span>消息通知</span>
            <el-badge v-if="unread > 0" :value="unread" :max="99" class="menu-badge" />
          </el-menu-item>
          <el-menu-item v-if="userStore.isAdmin" index="/template">
            <el-icon><Files /></el-icon>
            <span>模板管理</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-title">{{ $route.meta.title }}</div>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.userInfo?.realName || userStore.userInfo?.username }}
            <el-badge v-if="unread > 0" :value="unread" :max="99" class="header-badge">
              <el-icon><Bell /></el-icon>
            </el-badge>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 内容区 -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPwd">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'
import { changePassword } from '../api/auth'
import { getUnreadCount } from '../api/notify'

const router = useRouter()
const userStore = useUserStore()

/* 未读消息角标(教师+admin 可能有通知;viewer 不拉) */
const unread = ref(0)
let unreadTimer = null

async function loadUnread() {
  if (userStore.userInfo?.role === 'viewer') return
  try {
    unread.value = await getUnreadCount()
  } catch (e) {
    /* 静默失败,不打扰 */
  }
}

onMounted(() => {
  loadUnread()
  unreadTimer = setInterval(loadUnread, 60000)
})

onBeforeUnmount(() => clearInterval(unreadTimer))

function handleCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗?', '提示', { type: 'warning' })
      .then(() => {
        userStore.logout()
        router.push('/login')
      })
      .catch(() => {})
  } else if (cmd === 'password') {
    pwdVisible.value = true
  }
}

/* 修改密码 */
const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6~32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function submitPwd() {
  await pwdFormRef.value.validate()
  pwdLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功,请重新登录')
    userStore.logout()
    router.push('/login')
  } catch (e) {
    // 错误已统一提示
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background-color: #1d2b3a;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 2px;
}

.aside .el-menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
}

.menu-badge {
  margin-left: 8px;
}

.header-badge {
  margin-right: 4px;
}

.el-main {
  padding: 16px;
}
</style>
