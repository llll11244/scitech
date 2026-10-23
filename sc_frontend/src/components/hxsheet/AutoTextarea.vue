<template>
  <div class="cellwrap" :style="wrapStyle">
    <textarea v-if="mode === 'edit'" ref="ta" class="cellarea" :value="modelValue" :maxlength="maxlength"
      :placeholder="placeholder" :style="areaStyle" @input="onInput" @blur="emit('blur')" />
    <div v-else class="para" :style="printStyle">{{ modelValue }}</div>
    <span v-if="mode === 'edit' && maxlength" class="scr charcount num">{{ len }}/{{ maxlength }}</span>
  </div>
</template>

<!--
  长文本单元格:编辑态=自动撑高 textarea(oninput height=scrollHeight,打印即纯文本),
  超过 maxH(约 300px)不再撑高,改出纵向滚动条(屏显滚轮,打印仍是全量文本);
  打印态=纯文本 div(保留 justify/缩进);字数计数仅屏显。
  softTip=软口径灰字(如「建议 200-300 字」),由调用方放 hint 层,本组件不管。
-->
<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  maxlength: { type: Number, default: 0 },
  placeholder: { type: String, default: '' },
  mode: { type: String, default: 'edit' },
  minH: { type: String, default: '' }        // 打印保底高度(如 24mm)
})
const emit = defineEmits(['update:modelValue', 'blur'])

const MAX_H = 300   // 编辑态限高(px):超出出滚动条,避免长文把表格无限撑长

const ta = ref(null)
const len = computed(() => (props.modelValue || '').length)
const wrapStyle = computed(() => (props.minH ? { minHeight: props.minH } : {}))
const printStyle = computed(() => ({}))
const areaStyle = computed(() => ({
  ...(props.minH ? { minHeight: props.minH } : {}),
  maxHeight: MAX_H + 'px',
  overflowY: 'auto'
}))

function autosize(el) {
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, MAX_H) + 'px'
}

function onInput(e) {
  autosize(e.target)
  emit('update:modelValue', e.target.value)
}

onMounted(async () => {
  await nextTick()
  if (ta.value) autosize(ta.value)
})
</script>
