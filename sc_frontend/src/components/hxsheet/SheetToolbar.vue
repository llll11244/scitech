<template>
  <div class="btoolbar">
    <label v-if="hints" class="hintswitch">
      <input type="checkbox" :checked="showHints"
        @change="$emit('update:showHints', $event.target.checked)">显示填写提示
    </label>
    <slot></slot>
  </div>
</template>

<!--
  单据工具条(hx-sheet 体系公共件):悬浮钉表单容器左上(样式见 hx-sheet.css .btoolbar),
  左侧提示开关+右侧动作按钮(slot);打印隐藏。四张表单页与打印页统一此逻辑。
-->
<script setup>
defineProps({
  showHints: { type: Boolean, default: true },
  hints: { type: Boolean, default: true }   // false=不带提示开关(打印页)
})
defineEmits(['update:showHints'])
</script>
