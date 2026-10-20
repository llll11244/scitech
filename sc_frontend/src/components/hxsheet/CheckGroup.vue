<template>
  <span>
    <span v-for="o in options" :key="o" class="opt" :class="{ on: isActive(o) }"
      @click="mode === 'edit' && toggle(o)">{{ isActive(o) ? '√' : '□' }}{{ o }}</span><span>&emsp;</span>
  </span>
</template>

<!--
  勾选组(演示稿 .opt 同款):span 点击切 √/□,打印输出当前状态文本;
  单选组=同组互斥,multi 组可多选(再点取消)。
  modelValue:单选=string(选项值,空串=未选);多选=string[](与后端逗号串互转由调用方做)。
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  options: { type: Array, required: true },
  multiple: { type: Boolean, default: false },
  modelValue: { type: [String, Array], default: '' },
  mode: { type: String, default: 'edit' }   // edit=可点;print=只读
})
const emit = defineEmits(['update:modelValue'])

const arr = computed(() => (Array.isArray(props.modelValue) ? props.modelValue
  : String(props.modelValue || '').split(',').filter(Boolean)))

function isActive(o) {
  return props.multiple ? arr.value.includes(o) : props.modelValue === o
}

function toggle(o) {
  if (props.multiple) {
    const next = arr.value.includes(o) ? arr.value.filter((x) => x !== o) : [...arr.value, o]
    emit('update:modelValue', next)
  } else {
    emit('update:modelValue', props.modelValue === o ? '' : o)
  }
}
</script>
