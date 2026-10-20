<template>
  <div class="hx-sheet" :class="{ nohints: !showHints }">
    <div class="watermark"><img src="/sheet-assets/watermark.png" alt=""></div>

    <div class="page">
      <div class="toparea">
        <img class="hlogo" src="/sheet-assets/logo.png" alt="成都轻工职业技术大学">
        <b>成都轻工职业技术大学横向项目验收登记表</b>
      </div>

      <table class="ftab">
        <colgroup><col style="width:17%"><col style="width:22%"><col style="width:14%"><col style="width:16%"><col style="width:13%"><col></colgroup>
        <tbody>
          <tr>
            <td class="lbl">项目编号</td><td class="num">{{ meta.orig.approvalNo }}</td>
            <td class="lbl">项目名称</td><td colspan="3">{{ meta.orig.name }}</td>
          </tr>
          <tr>
            <td class="lbl">项目类型</td>
            <td class="chk" colspan="5"><CheckGroup v-model="model.projectType"
              :options="meta.dictProjectType" mode="print" /></td>
          </tr>
          <tr>
            <td class="lbl">起止时间</td>
            <td colspan="5">{{ cnMonth(meta.orig.startDate) }}至{{ cnMonth(meta.orig.endDate) }}</td>
          </tr>
          <tr>
            <td class="lbl" rowspan="2">委托单位</td><td>名称:{{ meta.orig.clientName }}</td>
            <td class="lbl">性质</td>
            <td class="chk" colspan="3"><CheckGroup v-model="model.clientNature"
              :options="meta.dictClientNature" mode="print" /></td>
          </tr>
          <tr>
            <td class="lbl">到账经费(万元)</td><td class="num">{{ toWan(meta.receivedTotal) || '—' }}</td>
            <td class="lbl">使用经费(万元)</td><td class="num">
              <input v-if="mode === 'edit'" v-model.number="model.usedAmount" type="number" min="0"
                step="0.01" class="cellinput num" style="width:100px" placeholder="必填">
              <template v-else>{{ toWan(model.usedAmount) }}</template>
              <span v-if="mode === 'edit'" class="hint">到账由科技处录入,按到账明细累计(只读)</span>
            </td>
          </tr>
          <tr>
            <td class="lbl">项目负责人</td><td>{{ meta.orig.leaderName }}</td>
            <td class="lbl">所在院部</td><td colspan="3">{{ meta.orig.deptName }}</td>
          </tr>

          <!-- ===== B 项目完成情况(成员比例表,统一 5 列,校内/校外分组可增删) ===== -->
          <tr>
            <td class="lbl" :rowspan="3 + innerMembers.length + outerMembers.length">项目完成情况</td>
            <td class="subhdr">姓名</td><td class="subhdr">所在院部</td><td class="subhdr">职称</td>
            <td class="subhdr">学科专业</td><td class="subhdr">承担本项目的工作任务比例(%及金额)
              <div class="hint">需明确百分比和金额,单位万元。</div></td>
          </tr>
          <tr><td class="grp" colspan="5">校内人员</td></tr>
          <tr v-for="(m, i) in innerMembers" :key="'in' + i">
            <td><input v-if="mode === 'edit'" v-model.trim="m.name" class="cellinput" maxlength="64" placeholder="姓名">
              <template v-else>{{ m.name }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.unit" class="cellinput" maxlength="255" placeholder="所在院部">
              <template v-else>{{ m.unit }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.title" class="cellinput" maxlength="32" placeholder="职称">
              <template v-else>{{ m.title }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.major" class="cellinput" maxlength="64" placeholder="学科专业">
              <template v-else>{{ m.major }}</template></td>
            <td class="num">
              <template v-if="mode === 'edit'">
                <input v-model.number="m.ratio" type="number" min="0" max="100" step="0.01"
                  class="cellinput num" style="width:56px" placeholder="%">%(<input v-model.number="m.amount"
                  type="number" min="0" step="0.01" class="cellinput num" style="width:90px" placeholder="元">元,
                <a class="scr rowbtn" @click="byRatio(m)">按比例算</a>)<a class="scr rowbtn" @click="removeMember('in', i)">✕</a>
              </template>
              <template v-else>{{ ratioText(m) }}</template>
            </td>
          </tr>
          <tr v-if="mode === 'edit'"><td colspan="5"><a class="scr rowbtn" @click="addMember('in')">+ 添加校内人员</a></td></tr>
          <tr><td class="grp" colspan="5">校外人员</td></tr>
          <tr v-for="(m, i) in outerMembers" :key="'out' + i">
            <td><input v-if="mode === 'edit'" v-model.trim="m.name" class="cellinput" maxlength="64" placeholder="姓名">
              <template v-else>{{ m.name }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.unit" class="cellinput" maxlength="255" placeholder="所在单位">
              <template v-else>{{ m.unit }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.title" class="cellinput" maxlength="32" placeholder="职务/职称">
              <template v-else>{{ m.title }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.major" class="cellinput" maxlength="64" placeholder="学科专业">
              <template v-else>{{ m.major }}</template></td>
            <td class="num">
              <template v-if="mode === 'edit'">
                <input v-model.number="m.ratio" type="number" min="0" max="100" step="0.01"
                  class="cellinput num" style="width:56px" placeholder="%">%(<input v-model.number="m.amount"
                  type="number" min="0" step="0.01" class="cellinput num" style="width:90px" placeholder="元">元,
                <a class="scr rowbtn" @click="byRatio(m)">按比例算</a>)<a class="scr rowbtn" @click="removeMember('out', i)">✕</a>
              </template>
              <template v-else>{{ ratioText(m) }}</template>
            </td>
          </tr>
          <tr v-if="mode === 'edit'"><td colspan="5"><a class="scr rowbtn" @click="addMember('out')">+ 添加校外人员</a></td></tr>
          <tr style="font-weight:bold">
            <td>合计</td><td colspan="4" class="num">比例:{{ ratioSum.toFixed(2) }}%　金额:{{ money(amountSum) }} 元({{ toWan(amountSum) }} 万元)
              <span v-if="mode === 'edit' && (ratioSum !== 100 || amountSum !== Number(meta.receivedTotal))"
                class="scr" style="color:#f56c6c;margin-left:6px">须 比例=100% 且 金额=到账经费 {{ money(meta.receivedTotal) }} 元</span>
            </td>
          </tr>

          <!-- ===== C 学生参与情况 ===== -->
          <tr>
            <td class="lbl" :rowspan="2 + model.students.length">学生参与情况
              <div class="hint">尽可能让学生参与工作,紧扣立德树人根本任务。</div></td>
            <td class="subhdr">学生姓名</td><td class="subhdr">学号</td><td class="subhdr">专业</td>
            <td class="subhdr">指导老师</td><td class="subhdr">参与的工作任务
              <div class="hint">指具体参与什么工作。</div></td>
          </tr>
          <tr v-for="(s, i) in model.students" :key="'st' + i">
            <td><input v-if="mode === 'edit'" v-model.trim="s.name" class="cellinput" maxlength="64" placeholder="学生姓名">
              <template v-else>{{ s.name }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="s.studentNo" class="cellinput num" maxlength="32" placeholder="学号">
              <template v-else>{{ s.studentNo }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="s.major" class="cellinput" maxlength="64" placeholder="专业">
              <template v-else>{{ s.major }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="s.mentor" class="cellinput" maxlength="64" placeholder="指导老师">
              <template v-else>{{ s.mentor }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="s.duty" class="cellinput" maxlength="500" placeholder="参与的工作任务">
              <template v-else>{{ s.duty }}</template>
              <a v-if="mode === 'edit'" class="scr rowbtn" @click="model.students.splice(i, 1); touch()">✕</a></td>
          </tr>
          <tr v-if="mode === 'edit'"><td colspan="5"><a class="scr rowbtn" @click="addStudent">+ 添加学生</a></td></tr>

          <tr>
            <td class="lbl">完成时间</td>
            <td colspan="5" class="num">
              <input v-if="mode === 'edit'" v-model="model.finishDate" type="date" class="cellinput" style="width:150px">
              <template v-else>{{ cnDateFull(model.finishDate) }}</template>
            </td>
          </tr>
          <tr>
            <td class="lbl">成果形式</td>
            <td class="chk multi" colspan="5"><CheckGroup v-model="model.outcomeForms"
              :options="meta.dictOutcomeForm" :mode="mode" multiple /></td>
          </tr>
          <tr>
            <td class="lbl">项目完成情况总结</td>
            <td colspan="5">
              <AutoTextarea v-model="model.acceptSummary" :maxlength="1000" :mode="mode"
                min-h="28mm" placeholder="目标达成/关键成果/经费执行/客户评价" @blur="touch" />
              <details v-if="mode === 'edit'" class="hint">
                <summary>示例:完成情况总结怎么写</summary>
                <ul>
                  <li>目标达成:合同约定内容全部完成,指标达到 XX。</li>
                  <li>关键成果:交付 XX 文档/样机/专利 XX 项。</li>
                  <li>经费执行:使用经费 XX 万元,执行率 XX%。</li>
                  <li>客户评价:委托方验收合格,反馈良好。</li>
                </ul>
              </details>
              <div class="inline-sign">项目负责人(签字):&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;年&emsp;月&emsp;日</div>
            </td>
          </tr>
          <tr>
            <td class="lbl">项目负责人所在单位意见</td>
            <td colspan="5" class="sign-block">
              <div class="hint">参考措辞(纸面留白手写):情况属实,同意验收。</div>
              <div class="sign-line">负责人(签字):(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
          </tr>
          <tr>
            <td class="lbl">委托单位对项目完成情况的评价意见,是否验收合格。</td>
            <td colspan="5" class="sign-block">
              <div class="hint">参考措辞(纸面留白手写):项目团队已于合同约定期内提交全部成果,所有项目资料经由我单位核查无误,资金使用规范合理,验收合格。</div>
              <div class="sign-line">委托单位(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
          </tr>
          <tr>
            <td class="lbl" rowspan="2">科技处意见</td>
            <td class="lbl">到账金额核定(万元)</td>
            <td colspan="2" class="num">{{ meta.verifiedAmountWan || '' }}</td>
            <td colspan="2">核定人签字:____　核定人:{{ meta.verifierName || '' }}</td>
          </tr>
          <tr>
            <td colspan="5" class="sign-block">
              <div class="sign-line">负责人(签字)(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="footnote">本表一式四份:委托单位一份,科技处一份,所在单位一份,项目负责人一份,双面打印,控制在两页内。</div>
    </div>
  </div>
</template>

<!--
  验收登记表单据组件(演示稿 hx_acceptance.html 转正):到账经费=系统 SUM 只读;
  成员比例表校内/校外分组可增删,「按比例算」快捷=到账×比例;合计行实时校验
  (比例=100%、金额=到账,提交硬校验在后端);学生表可增删;科技处核定行仅通过后带出。
-->
<script setup>
import { computed } from 'vue'
import { toWan } from '../../utils/rmb'
import AutoTextarea from './AutoTextarea.vue'
import CheckGroup from './CheckGroup.vue'

const props = defineProps({
  model: { type: Object, required: true },   // {usedAmount,finishDate,outcomeForms[],acceptSummary,members[],students[]}
  mode: { type: String, default: 'edit' },
  showHints: { type: Boolean, default: true },
  meta: { type: Object, default: () => ({ orig: {}, receivedTotal: null, dictOutcomeForm: [],
    dictProjectType: [], dictClientNature: [], verifiedAmountWan: '', verifierName: '' }) }
})
const emit = defineEmits(['change'])

const money = (v) => (v === null || v === undefined || v === '' ? '' : Number(v).toFixed(2))
const cnMonth = (s) => {
  if (!s) return ''
  const [y, m] = String(s).slice(0, 10).split('-')
  return `${y}年${String(Number(m)).padStart(2, '0')}月`
}
const cnDateFull = (s) => {
  if (!s) return ''
  const [y, m, d] = String(s).slice(0, 10).split('-')
  return `${y}年${Number(m)}月${Number(d)}日`
}

const innerMembers = computed(() => props.model.members.filter((m) => m.memberType !== '校外人员'))
const outerMembers = computed(() => props.model.members.filter((m) => m.memberType === '校外人员'))
const ratioSum = computed(() => props.model.members.reduce((s, m) => s + (Number(m.ratio) || 0), 0))
const amountSum = computed(() => props.model.members.reduce((s, m) => s + (Number(m.amount) || 0), 0))

function ratioText(m) {
  const r = m.ratio === null || m.ratio === undefined ? '' : String(Number(m.ratio))
  const amt = m.amount === null || m.amount === undefined ? '' : `(${toWan(m.amount)}万元)`
  return `${r}%${amt}`
}

function addMember(kind) {
  props.model.members.push({
    memberType: kind === 'in' ? '教师' : '校外人员', name: '', unit: '', title: '', major: '',
    duty: '', ratio: null, amount: null
  })
  touch()
}
function removeMember(kind, i) {
  const list = kind === 'in' ? innerMembers.value : outerMembers.value
  props.model.members.splice(props.model.members.indexOf(list[i]), 1)
  touch()
}
function byRatio(m) {
  const r = Number(m.ratio)
  const total = Number(props.meta.receivedTotal)
  if (!isFinite(r) || !isFinite(total) || !total) return
  m.amount = +(total * r / 100).toFixed(2)
  touch()
}
function addStudent() {
  props.model.students.push({ memberType: '学生', name: '', studentNo: '', major: '', mentor: '', duty: '' })
  touch()
}
function touch() { emit('change') }
</script>
