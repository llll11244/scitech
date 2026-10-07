<template>
  <div ref="hostRef" class="docx-pane"></div>
</template>

<!--
  docx 渲染容器(三张填报表单 + 提交确认弹窗共用):
  父组件 fetch blob → 传入 :blob;渲染完成 emit('rendered', 页数)。
  inWrapper=false 不用库自带灰底包裹层,页面观感由本组件 scoped 样式接管。
  弹窗场景首挂载时 blob 已就位(watch 不会再触发),故 onMounted 先渲一次初始 blob。
-->
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { renderAsync } from 'docx-preview'

const props = defineProps({
  blob: { type: [Blob, Object], default: null }
})
const emit = defineEmits(['rendered'])

const hostRef = ref(null)
let seq = 0 // 渲染序号:快速连续换 blob 时只保留最后一次结果

async function renderBlob(b) {
  if (!b || !hostRef.value) return
  const cur = ++seq
  hostRef.value.innerHTML = ''
  try {
    await renderAsync(b, hostRef.value, null, { inWrapper: false, breakPages: true })
    if (cur !== seq) return // 已有更新一次渲染,本次作废
    emit('rendered', hostRef.value.querySelectorAll('section.docx').length)
  } catch (e) {
    // 渲染失败不打断填报表单,控制台留痕即可
    console.error('docx 预览渲染失败:', e)
  }
}

onMounted(() => renderBlob(props.blob))
watch(() => props.blob, renderBlob)

onBeforeUnmount(() => {
  if (hostRef.value) hostRef.value.innerHTML = ''
})
</script>

<style scoped>
.docx-pane {
  min-height: 120px;
}

/* 仿纸面页(库注入的 .docx section 只有宽高与白底,观感在此补齐) */
.docx-pane :deep(section.docx) {
  background: #fff;
  margin: 0 auto 14px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.16);
}
</style>
