<template>
  <el-card shadow="never">
    <template #header>
      <div class="bar">
        <span>消息通知(未读 {{ unread }} 条)</span>
        <el-button size="small" :disabled="!rows.length" @click="markAll">全部已读</el-button>
      </div>
    </template>

    <el-empty v-if="!loading && !rows.length" description="暂无消息" />

    <div v-else v-loading="loading" class="list">
      <div
        v-for="n in rows"
        :key="n.id"
        class="item"
        :class="{ unread: n.isRead === 0 }"
        @click="open(n)"
      >
        <div class="item-head">
          <span class="dot" v-if="n.isRead === 0" />
          <span class="title">{{ n.title }}</span>
          <span class="time">{{ n.createTime }}</span>
        </div>
        <div class="content">{{ n.content }}</div>
      </div>
    </div>

    <div class="pager" v-if="total > size">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="current"
        @current-change="load"
      />
    </div>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageNotify, readNotify, readAllNotify, getUnreadCount } from '../api/notify'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const unread = ref(0)
const current = ref(1)
const size = 15

async function load(page = 1) {
  current.value = page
  loading.value = true
  try {
    const data = await pageNotify({ current: page, size })
    rows.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

/** 刷新未读数(轻量接口) */
async function refreshUnread() {
  try {
    unread.value = await getUnreadCount()
  } catch (e) { /* 静默 */ }
}

/** 点击消息:标已读;带流程单的通知跳流程详情(教师/admin 均可访问 FlowDetail) */
async function open(n) {
  if (n.isRead === 0) {
    try {
      await readNotify(n.id)
      n.isRead = 1
      refreshUnread()
    } catch (e) { /* 已读失败不阻塞跳转 */ }
  }
  if (n.flowId) {
    router.push(`/hx/flow/${n.flowId}`)
  }
}

async function markAll() {
  await readAllNotify()
  ElMessage.success('已全部标记为已读')
  load(current.value)
  refreshUnread()
}

onMounted(() => {
  load(1)
  refreshUnread()
})
</script>

<script>
export default { name: 'Notify' }
</script>

<style scoped>
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list {
  max-width: 860px;
}

.item {
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.item.unread {
  border-left: 3px solid #2a78d6;
  background: #f5f9ff;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
}

.title {
  font-weight: 600;
  color: #303133;
}

.time {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}

.content {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.pager {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
