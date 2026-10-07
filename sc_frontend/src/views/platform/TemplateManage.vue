<template>
  <div class="tpl-manage">
    <!-- 类型切换 -->
    <el-card shadow="never" class="mb">
      <el-radio-group v-model="tplType" @change="load">
        <el-radio-button v-for="t in TPL_TYPES" :key="t.type" :value="t.type">
          {{ t.label }}
        </el-radio-button>
      </el-radio-group>

      <!-- 上传区 -->
      <div class="upload-row">
        <el-upload
          :show-file-list="false"
          :auto-upload="false"
          accept=".docx"
          :on-change="onFilePicked"
        >
          <el-button type="primary" :loading="uploading">
            {{ uploading ? '校验渲染中(约10秒)…' : '上传新版模板(.docx)' }}
          </el-button>
        </el-upload>
        <el-input
          v-model="remark"
          placeholder="版本备注(选填,如:调整简介栏行高)"
          class="remark"
          maxlength="100"
        />
      </div>
      <el-alert type="info" :closable="false" class="mt"
        title="上传校验:必须是 .docx;占位符需与《占位符清单》完全一致(缺失会明确列出);系统用内置样例数据试渲染并校验页数(登记/变更/验收 ≤2 页)。通过后新版本立即启用,旧版转历史;已归档 PDF 不受影响。" />
    </el-card>

    <!-- 版本列表 -->
    <el-card shadow="never">
      <template #header>
        <span>版本历史 · {{ currentLabel }}</span>
      </template>
      <el-table :data="rows" v-loading="loading" size="default">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">v{{ row.version }} 启用中</el-tag>
            <el-tag v-else type="info">v{{ row.version }} 历史</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="placeholderCount" label="占位符" width="90" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" width="170">
          <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="preview(row)">样例预览</el-button>
            <el-button link @click="download(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="'样例渲染预览 · ' + currentLabel" width="86%" top="3vh" destroy-on-close @closed="revoke">
      <iframe v-if="previewUrl" :src="previewUrl" class="pdf-frame" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { TPL_TYPES, downloadTemplate, listTemplates, previewTemplate, uploadTemplate } from '../../api/template'

const tplType = ref('hx_register')
const remark = ref('')
const rows = ref([])
const loading = ref(false)
const uploading = ref(false)

const previewVisible = ref(false)
const previewUrl = ref('')
let objectUrl = null

const currentLabel = computed(() => TPL_TYPES.find(t => t.type === tplType.value)?.label || '')

async function load() {
  loading.value = true
  try {
    rows.value = (await listTemplates(tplType.value)) || []
    rows.value.forEach(r => {
      try { r.placeholderCount = JSON.parse(r.placeholderCheck || '[]').length } catch { r.placeholderCount = '-' }
    })
  } finally {
    loading.value = false
  }
}

function onFilePicked(file) {
  const raw = file.raw
  if (!raw) return
  if (!raw.name.toLowerCase().endsWith('.docx')) {
    ElMessage.warning('请上传 .docx 模板文件')
    return
  }
  doUpload(raw)
}

async function doUpload(file) {
  uploading.value = true
  try {
    await uploadTemplate(file, tplType.value, remark.value)
    ElMessage.success('上传成功,新版本已启用')
    remark.value = ''
    await load()
  } catch (e) {
    // 失败信息(缺占位符明细等)已由拦截器弹出
  } finally {
    uploading.value = false
  }
}

async function preview(row) {
  try {
    const blob = await previewTemplate(row.id)
    objectUrl = URL.createObjectURL(blob)
    previewUrl.value = objectUrl
    previewVisible.value = true
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function download(row) {
  try {
    const blob = await downloadTemplate(row.id)
    const a = document.createElement('a')
    const url = URL.createObjectURL(blob)
    a.href = url
    a.download = `${row.tplType}_v${row.version}.docx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function revoke() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = null
    previewUrl.value = ''
  }
}

onMounted(load)
onBeforeUnmount(revoke)
</script>

<style scoped>
.mb {
  margin-bottom: 14px;
}

.mt {
  margin-top: 12px;
}

.upload-row {
  margin-top: 14px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.remark {
  width: 320px;
}

.pdf-frame {
  width: 100%;
  height: 76vh;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
</style>
