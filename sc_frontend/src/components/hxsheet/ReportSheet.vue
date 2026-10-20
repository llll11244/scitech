<template>
  <div class="hx-sheet" :class="{ nohints: !showHints }">
    <div class="watermark"><img src="/sheet-assets/watermark.png" alt=""></div>

    <div class="page">
      <div class="toparea">
        <img class="hlogo" src="/sheet-assets/logo.png" alt="成都轻工职业技术大学">
        <b>横向科研项目验收报告</b>
      </div>

      <table class="ftab">
        <colgroup><col style="width:17%"><col style="width:33%"><col style="width:15%"><col></colgroup>
        <tbody>
          <tr>
            <td class="lbl">项目编号</td><td class="num">{{ meta.orig.approvalNo }}</td>
            <td class="lbl">项目名称</td><td>{{ meta.orig.name }}</td>
          </tr>
          <tr>
            <td class="lbl">是否按期完成</td>
            <td class="chk"><CheckGroup v-model="onTimeFlag" :options="['是', '否']" :mode="mode" /></td>
            <td class="lbl">项目类型</td>
            <td class="chk"><CheckGroup v-model="model.projectType" :options="meta.dictProjectType" mode="print" /></td>
          </tr>
          <tr>
            <td class="lbl">成果形式</td>
            <td class="chk multi" colspan="3"><CheckGroup v-model="model.outcomeForms"
              :options="meta.dictOutcomeForm" :mode="mode" multiple /></td>
          </tr>
        </tbody>
      </table>

      <div class="report-body">
        <template v-if="mode === 'edit'">
          <div class="scr rep-tools">
            插入章节:
            <a v-for="c in chapters" :key="c" class="rowbtn" @click="insertChapter(c)">{{ c }}</a>
          </div>
          <details class="hint">
            <summary>写作大纲参考(如有图纸、图片等其它材料,插入本报告或另附亦可;页数不限,多页单面打印)</summary>
            <ul>
              <li><b>一、项目目标达成情况:</b>内容完成度/节点执行/标准合规</li>
              <li><b>二、研究过程总结:</b>流程/协作/质控</li>
              <li><b>三、关键工作成果:</b>技术/知产/亮点</li>
              <li><b>四、进度与经费执行</b></li>
              <li><b>五、客户评价与效益:</b>甲方评价/经济/社会</li>
              <li><b>六、总结与展望</b></li>
              <li><b>七、结论</b></li>
            </ul>
          </details>
          <textarea ref="taRef" v-model="model.reportContent" class="cellarea rep-area" maxlength="20000"
            placeholder="按章节撰写报告正文(空行分段;章节标题独占一行)" @input="autosize" @blur="emit('blur')" />
          <div class="scr num" style="text-align:right">{{ (model.reportContent || '').length }}/20000</div>
        </template>
        <template v-else>
          <div class="hint" style="margin-bottom:6px">(包括项目成果内容提要、研究过程总结、项目获奖情况、成果推广计划等,页数不限。)</div>
          <template v-for="(p, i) in paragraphs" :key="i">
            <p v-if="p.sub" class="sub">{{ p.text }}</p>
            <p v-else>{{ p.text }}</p>
          </template>
        </template>

        <div class="sign-block" style="margin-top:18px">
          <div class="sign-line">项目负责人(签字):&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;年&emsp;月&emsp;日</div>
        </div>
      </div>

      <div class="footnote">注:本报告页数不限,多页时单面打印;签名处须手写。</div>
    </div>
  </div>
</template>

<!--
  验收报告单据组件(演示稿 hx_report.html 转正):表头小表+正文区。
  正文=单文本编辑(空行分段,章节标题独占行「一、…」;工具条快捷插入章节),
  存储与旧 report_content(\n\n 分段)完全兼容;打印按行识别章节标题渲染 p.sub,页数不限。
-->
<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import CheckGroup from './CheckGroup.vue'

const props = defineProps({
  model: { type: Object, required: true },   // {projectType,outcomeForms[],reportContent,isOnTime}
  mode: { type: String, default: 'edit' },
  showHints: { type: Boolean, default: true },
  meta: { type: Object, default: () => ({ orig: {}, dictProjectType: [], dictOutcomeForm: [] }) }
})
const emit = defineEmits(['change', 'blur'])

const chapters = ['一、项目目标达成情况', '二、研究过程总结', '三、关键工作成果', '四、进度与经费执行',
  '五、客户评价与效益', '六、总结与展望', '七、结论']

const onTimeFlag = computed({
  get: () => (props.model.isOnTime === 1 ? '是' : props.model.isOnTime === 0 ? '否' : ''),
  set: (v) => { props.model.isOnTime = v === '是' ? 1 : 0; emit('change') }
})

/* 打印态分段:空行分段;行首「一、二、…七、」识别为章节标题 */
const SUB_RE = /^[一二三四五六七八九十]+、/
const paragraphs = computed(() => (props.model.reportContent || '')
  .split(/\n{2,}|\r\n{2,}/)
  .map((s) => s.trim())
  .filter(Boolean)
  .map((text) => ({ text, sub: SUB_RE.test(text) })))

/* 编辑态:自动撑高(编辑器高度独立于 .para 口径,正文区自然长) */
const taRef = ref(null)
function autosize(e) {
  const el = e ? e.target : taRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
  emit('change')
}
function insertChapter(c) {
  const el = taRef.value
  const cur = props.model.reportContent || ''
  props.model.reportContent = (cur ? cur.replace(/\s*$/, '') + '\n\n' : '') + c + '\n\n'
  nextTick(() => autosize())
  emit('change')
}
onMounted(async () => {
  await nextTick()
  if (taRef.value) autosize()
})
</script>

<style scoped>
.rep-tools {
  margin: 4px 0;
}
.rep-area {
  width: 100%;
  min-height: 420px;
  margin-top: 4px;
}
</style>
