<template>
  <div class="backup-manage">
    <!-- 状态卡:自检结果+定时策略+保留策略 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="bar">
          <span>数据备份</span>
          <div>
            <el-button :loading="loading" @click="refresh">刷新</el-button>
            <el-button type="primary" :disabled="!status.available || running"
              :loading="running" @click="run">立即备份</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!status.available" type="warning" :closable="false" class="mb"
        :title="'备份组件不可用,立即备份已禁用:' + (status.message || '未完成自检')" />
      <el-alert v-else-if="status.message" type="success" :closable="false" class="mb"
        :title="status.message" />

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="定时策略">{{ cronText }}</el-descriptions-item>
        <el-descriptions-item label="保留策略">最多 {{ status.keep || '-' }} 份,超出删最旧</el-descriptions-item>
        <el-descriptions-item label="备份内容">全库 SQL + upload/ 附件目录</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 备份列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" size="default" empty-text="暂无备份">
        <el-table-column prop="name" label="文件名" min-width="240" />
        <el-table-column prop="time" label="备份时间" width="170" />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ humanSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="download(row)">下载</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<!--
  备份管理(spec §八/决策#34-①):定时(每周一 02:30,cron 可配)+手动异步;
  备份包=mysqldump 全库+upload/ 打 zip,保留 8 份删最旧;仅 admin。
  手动触发后 running 轮询 status 直到完成再刷新列表(后端异步执行,二次触发返回"进行中")。
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  backupDelete, backupDownload, backupList, backupRun, backupStatus
} from '../../api/backup'

const loading = ref(false)
const running = ref(false)
const status = ref({ available: false, message: '', cron: '', keep: 8, running: false })
const rows = ref([])
let pollTimer = null

/* cron 转可读文案(Spring 6 段=秒 分 时 日 月 周 / 传统 5 段;仅覆盖每天/每周形态,其余原样) */
const cronText = computed(() => {
  const c = (status.value.cron || '').trim()
  const parts = c.split(/\s+/)
  let min, hour, day, month, week
  if (parts.length === 6) [, min, hour, day, month, week] = parts
  else if (parts.length === 5) [min, hour, day, month, week] = parts
  else return c || '-'
  if (day !== '*' || month !== '*') return c
  const time = String(hour).padStart(2, '0') + ':' + String(min).padStart(2, '0')
  const wk = { MON: '一', TUE: '二', WED: '三', THU: '四', FRI: '五', SAT: '六', SUN: '日' }
  if (week && week !== '*') return `每周${wk[week.toUpperCase()] || week} ${time}`
  return `每天 ${time}`
})

async function refresh() {
  loading.value = true
  try {
    const [st, list] = await Promise.all([backupStatus(), backupList()])
    status.value = st
    rows.value = list || []
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

/** 手动备份:触发后轮询 status.running,完成自动刷新列表 */
async function run() {
  try {
    const data = await backupRun()
    if (data?.started) {
      running.value = true
      ElMessage.success(data.message || '备份已开始')
      pollTimer = setInterval(async () => {
        try {
          const st = await backupStatus()
          status.value = st
          if (!st.running) {
            stopPoll()
            await refresh()
            ElMessage.success('备份完成')
          }
        } catch (e) { /* 网络抖动下轮继续 */ }
      }, 3000)
    } else {
      ElMessage.info(data?.message || '已有备份进行中')
    }
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  running.value = false
}

async function download(row) {
  try {
    const blob = await backupDownload(row.name)
    const a = document.createElement('a')
    const url = URL.createObjectURL(blob)
    a.href = url
    a.download = row.name
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除备份「${row.name}」?该操作物理删除不可恢复。`, '提示',
      { type: 'warning' })
  } catch { return }
  try {
    await backupDelete(row.name)
    ElMessage.success('已删除')
    await refresh()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function humanSize(n) {
  if (!n && n !== 0) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

onMounted(refresh)
onBeforeUnmount(stopPoll)
</script>

<script>
export default { name: 'BackupManage' }
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
</style>
