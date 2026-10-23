<template>
  <div class="hx-sheet" :class="{ nohints: !showHints }">
    <div class="watermark"><img src="/sheet-assets/watermark.png" alt=""></div>

    <div class="page">
      <div class="toparea">
        <img class="hlogo" src="/sheet-assets/logo.png" alt="成都轻工职业技术大学">
        <b>成都轻工职业技术大学横向项目重要事项变更表</b>
      </div>

      <table class="ftab">
        <colgroup><col style="width:17%"><col style="width:33%"><col style="width:15%"><col></colgroup>
        <tbody>
          <tr>
            <td class="lbl">项目名称</td><td colspan="3">{{ meta.orig.name }}</td>
          </tr>
          <tr>
            <td class="lbl">项目编号</td><td class="num">{{ meta.orig.approvalNo }}</td>
            <td class="lbl">项目类别</td><td class="chk"><CheckGroup v-model="model.projectType"
              :options="meta.dictProjectType" mode="print" /></td>
          </tr>
          <tr>
            <td class="lbl">项目负责人</td><td>{{ meta.orig.leaderName }}</td>
            <td class="lbl">联系方式</td><td class="num">{{ meta.orig.leaderPhone }}</td>
          </tr>
          <tr>
            <td class="lbl">委托单位</td><td colspan="3">{{ meta.orig.clientName }}(性质:{{ meta.orig.clientNature }})</td>
          </tr>
          <tr>
            <td class="lbl">变更事项</td>
            <td class="chk multi" colspan="3"><CheckGroup v-model="model.changeTypes"
              :options="meta.dictChangeType" :mode="mode" multiple /></td>
          </tr>
          <tr>
            <td class="lbl">变更内容</td>
            <td colspan="3">
              <!-- 勾选联动:带出原信息行 -->
              <div v-for="o in activeOrigs" :key="o.key" class="oriline">· {{ o.label }} 原:{{ o.value }}</div>
              <div v-if="!model.changeTypes.length" class="oriline hint">尚未勾选变更事项——勾选后自动带出对应项原信息</div>

              <!-- 联动录入:勾了哪项就填哪项 -->
              <div v-if="mode === 'edit' && has('项目经费')" class="scr" style="margin:4px 0">
                变更后经费(元):<input v-model.number="model.newAmount" type="number" min="0" step="0.01"
                  class="cellinput num" style="width:130px" placeholder="必填">
              </div>
              <div v-if="mode === 'edit' && has('执行周期')" class="scr" style="margin:4px 0">
                变更后结束时间:<input v-model="model.newEndDate" type="date" class="cellinput num" style="width:150px">
              </div>
              <div v-if="mode === 'edit' && has('项目负责人')" class="scr" style="margin:4px 0">
                变更后负责人:<select v-model="model.newLeaderId" class="cellinput" style="width:220px">
                  <option value="">— 选择账号 —</option>
                  <option v-for="l in leaders" :key="l.id" :value="l.id">{{ l.name }}({{ l.username }})</option>
                </select>
                <input v-model.trim="leaderQuery" class="cellinput" style="width:160px; margin-left:6px"
                  placeholder="输姓名/工号搜索" @input="searchLeaders">
              </div>
              <div v-if="model.newAmount !== null && model.newAmount !== undefined && model.newAmount !== '' && has('项目经费')"
                class="num" style="margin-top:2px">
                变更为:人民币 {{ money(model.newAmount) }} 元(大写:{{ rmbCapital(model.newAmount) }})
              </div>
              <div v-if="model.newEndDate && has('执行周期')" style="margin-top:2px">
                变更为:{{ cnDateFull(model.newEndDate) }}
              </div>
              <div v-if="model.newLeaderId && has('项目负责人')" style="margin-top:2px">
                变更为:{{ leaderNameOf(model.newLeaderId) }}
              </div>

              <AutoTextarea v-model="model.changeContent" :maxlength="500" :mode="mode"
                min-h="18mm" placeholder="填写变更后内容(如:经费由 355000 元调整为 420000 元,其余条款不变)"
                style="margin-top:4px" @blur="touch" />
              <details v-if="mode === 'edit'" class="hint">
                <summary>示例:变更内容怎么写</summary>
                <ul>
                  <li>写明变更后内容与不变部分,如「因客户追加二期工位改造需求,合同经费由 355000 元调整为 420000 元,其余条款不变。」</li>
                  <li>涉知识产权的示例:专利等成果知识产权由「企业单独所有」修订为「双方共有」。</li>
                </ul>
              </details>
            </td>
          </tr>
          <tr>
            <td class="lbl">申请变更的原因</td>
            <td colspan="3">
              <AutoTextarea v-model="model.changeReason" :maxlength="300" :mode="mode"
                min-h="16mm" placeholder="填写申请变更的原因" @blur="touch" />
              <div class="inline-sign">申请人(签字):&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;年&emsp;月&emsp;日<br>
                变更后项目负责人(签字):&emsp;&emsp;&emsp;年&emsp;月&emsp;日</div>
            </td>
          </tr>
          <tr>
            <td class="lbl">委托单位意见</td>
            <td colspan="3" class="sign-block">
              <div class="hint">参考措辞(纸面留白手写):同意该项目重要事项的变更申请。</div>
              <div class="sign-line">负责人(签字):(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
          </tr>
          <tr>
            <td class="lbl">项目负责人所在单位意见</td>
            <td colspan="3" class="sign-block">
              <div class="hint">参考措辞(纸面留白手写):同意该项目重要事项的变更申请。</div>
              <div class="sign-line">负责人(签字):(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
          </tr>
          <tr>
            <td class="lbl">科技处意见</td>
            <td colspan="3" class="sign-block">
              <div class="sign-line">负责人(签字):(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="footnote">注:变更项目负责人、研究人员、合同,请填写此表。其中,变更人员的,请变更人员亲笔签名,各单位或部门盖章并由负责人签字。本表一式三份:科研处一份,所在部门一份,项目负责人一份。双面打印,控制在两页内(分页打印需委托方盖骑缝章)。</div>
    </div>
  </div>
</template>

<!--
  变更表单据组件(演示稿 hx_change.html 批次9 定稿转正):A 区基本信息=项目实态只读;
  变更事项 multi 勾选联动——勾选即带出原信息行(ORIG 由实态生成,不落库)+暴露对应录入位;
  签字盖章区纸面留白。
-->
<script setup>
import { computed, ref } from 'vue'
import { searchLeaders as searchLeadersApi } from '../../api/hx'
import { rmbCapital } from '../../utils/rmb'
import AutoTextarea from './AutoTextarea.vue'
import CheckGroup from './CheckGroup.vue'

const props = defineProps({
  model: { type: Object, required: true },   // {changeTypes[],changeContent,changeReason,newAmount,newEndDate,newLeaderId,projectType}
  mode: { type: String, default: 'edit' },
  showHints: { type: Boolean, default: true },
  meta: { type: Object, default: () => ({ orig: {}, dictChangeType: [], dictProjectType: [] }) }
})
const emit = defineEmits(['change'])

const money = (v) => (v === null || v === undefined || v === '' ? '' : Number(v).toFixed(2))
const cnDateFull = (s) => {
  if (!s) return ''
  const [y, m, d] = String(s).slice(0, 10).split('-')
  return `${y}年${Number(m)}月${Number(d)}日`
}

const has = (t) => (props.model.changeTypes || []).includes(t)

/* ORIG 原信息映射:由项目实态生成(委托内容=项目名称、其他约定=以合同约定为准,系统无合同文本) */
const ORIG = computed(() => ({
  fee: ['项目经费', `人民币 ${money(props.meta.orig.contractAmount)} 元(大写:${rmbCapital(props.meta.orig.contractAmount)})`],
  period: ['执行周期', `${cnRange(props.meta.orig.startDate)}至${cnRange(props.meta.orig.endDate)}`],
  leader: ['项目负责人', props.meta.orig.leaderName],
  content: ['委托内容', props.meta.orig.name],
  other: ['其他约定', '以合同约定为准']
}))
const KEYS = { 项目经费: 'fee', 执行周期: 'period', 项目负责人: 'leader', 委托内容: 'content', 其他约定: 'other' }
const activeOrigs = computed(() => (props.model.changeTypes || [])
  .map((t) => {
    const kv = ORIG.value[KEYS[t]]
    return kv ? { label: kv[0], value: kv[1] } : null
  })
  .filter(Boolean))

function cnRange(s) {
  if (!s) return ''
  const [y, m] = String(s).slice(0, 10).split('-')
  return `${y}年${String(Number(m)).padStart(2, '0')}月`
}

/* 负责人远程搜索(屏显辅助;打印输出所选姓名) */
const leaders = ref([])
const leaderQuery = ref('')
let searchTimer = null
function searchLeaders() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    if (!leaderQuery.value.trim()) return
    try {
      leaders.value = await searchLeadersApi(leaderQuery.value.trim())
    } catch (e) { /* 拦截器已提示 */ }
  }, 300)
}
const leaderNameOf = (id) => {
  const l = leaders.value.find((x) => x.id === id)
  return l ? `${l.name}(${l.username})` : ''
}

function touch() { emit('change') }
</script>

<style scoped>
.oriline {
  background: #F0FAFE;
  border-left: 3px solid #C9E9F9;
  padding: 2px 8px;
  margin: 2px 0;
}
</style>
