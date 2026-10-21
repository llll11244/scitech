<template>
  <div v-if="ready" class="print-root">
    <div class="btoolbar print-bar">
      <a @click="print">打印 / 另存为 PDF</a>
      <span class="tip">对话框:目标=另存为 PDF,布局=纵向,A4;若水印未输出请勾选「背景图形」</span>
    </div>

    <RegisterSheet v-if="flowType === 'register'" :model="model" mode="print"
      :show-hints="false" :meta="meta" />
    <ChangeSheet v-else-if="flowType === 'change'" :model="model" mode="print"
      :show-hints="false" :meta="meta" />
    <template v-else-if="flowType === 'acceptance'">
      <AcceptanceSheet v-if="doc !== 'hx_report'" :model="model" mode="print"
        :show-hints="false" :meta="meta" />
      <ReportSheet v-else :model="model" mode="print" :show-hints="false" :meta="meta" />
    </template>
  </div>
  <div v-else class="print-root loading">加载中…</div>
</template>

<!--
  打印页(HTML即交付 决策①):/print/:flowType/:flowId?doc=hx_report——
  Layout 外新标签打开,纯只读 Sheet(状态≥1 可打印;后台项通过后带出),加载完自动弹打印框。
-->
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { flowDetail } from '../api/hx'
import { buildSheetModel } from '../composables/sheetModel'
import AcceptanceSheet from '../components/hxsheet/AcceptanceSheet.vue'
import ChangeSheet from '../components/hxsheet/ChangeSheet.vue'
import RegisterSheet from '../components/hxsheet/RegisterSheet.vue'
import ReportSheet from '../components/hxsheet/ReportSheet.vue'

const route = useRoute()
const ready = ref(false)
const flowType = String(route.params.flowType || 'register')
const doc = String(route.query.doc || '')

const model = reactive({})
const meta = reactive({ orig: {} })

function print() {
  window.print()
}

onMounted(async () => {
  const d = await flowDetail(Number(route.params.flowId))
  const built = await buildSheetModel(d, flowType)
  Object.assign(model, built.model)
  Object.assign(meta, built.meta)
  ready.value = true
  setTimeout(() => window.print(), 300)
})
</script>

<style scoped>
.print-root {
  min-height: 100vh;
  background: #fff;
}
.loading {
  padding: 40px;
  color: #909399;
}
.print-bar {
  position: fixed;
}
</style>
