<template>
  <div class="hx-sheet" :class="{ nohints: !showHints }">
    <div class="watermark"><img src="/sheet-assets/watermark.png" alt=""></div>

    <div class="page">
      <div class="toparea">
        <img class="hlogo" src="/sheet-assets/logo.png" alt="成都轻工职业技术大学">
        <b>成都轻工职业技术大学横向科研项目登记表</b>
      </div>

      <table class="ftab">
        <colgroup><col style="width:13%"><col style="width:10%"><col style="width:8%"><col style="width:18%"><col style="width:10%"><col style="width:9%"><col></colgroup>
        <tbody>
          <tr>
            <td class="lbl">项目编号</td><td colspan="2">{{ meta.approvalNo || '' }}</td>
            <td class="lbl">项目名称</td><td colspan="3">
              <input v-if="mode === 'edit'" v-model.trim="model.name" class="cellinput" maxlength="100" placeholder="必填">
              <template v-else>{{ model.name }}</template>
            </td>
          </tr>
          <tr>
            <td class="lbl">项目类型</td>
            <td class="chk" colspan="6"><CheckGroup v-model="model.hx.projectType"
              :options="meta.dictProjectType" :mode="mode" /></td>
          </tr>
          <tr>
            <td class="lbl">起止时间</td>
            <td colspan="6">
              <template v-if="mode === 'edit'">
                <input v-model="model.hx.startDate" type="date" class="cellinput num" style="width:150px">
                <span style="margin:0 6px">至</span>
                <input v-model="model.hx.endDate" type="date" class="cellinput num" style="width:150px">
              </template>
              <template v-else>{{ cnDate(model.hx.startDate) }}至{{ cnDate(model.hx.endDate) }}</template>
            </td>
          </tr>
          <tr>
            <td class="lbl" rowspan="2">委托单位</td>
            <td colspan="6">名称:
              <input v-if="mode === 'edit'" v-model.trim="model.hx.clientName" class="cellinput"
                maxlength="255" style="width:70%" placeholder="必填">
              <template v-else>{{ model.hx.clientName }}</template>
            </td>
          </tr>
          <tr>
            <td class="lbl">性质</td>
            <td class="chk" colspan="5"><CheckGroup v-model="model.hx.clientNature"
              :options="meta.dictClientNature" :mode="mode" /></td>
          </tr>
          <tr>
            <td class="lbl">合同经费</td>
            <td colspan="6" class="num">
              <template v-if="mode === 'edit'">人民币
                <input v-model.number="model.hx.contractAmount" type="number" min="0" step="0.01"
                  class="cellinput num" style="width:130px" placeholder="必填(元)"> 元
              </template>
              <template v-else>人民币 {{ money(model.hx.contractAmount) }} 元</template>
              　(大写:{{ rmbCapital(model.hx.contractAmount) || '—' }})
            </td>
          </tr>
          <tr>
            <td class="lbl">项目负责人</td><td colspan="2">
              <input v-if="mode === 'edit'" v-model.trim="model.leaderName" class="cellinput" maxlength="64" placeholder="必填">
              <template v-else>{{ model.leaderName }}</template>
            </td>
            <td class="lbl">所属院部</td><td colspan="3">
              <select v-if="mode === 'edit'" v-model="model.deptId" class="cellinput">
                <option v-for="d in meta.depts" :key="d.id" :value="d.id">{{ d.name }}</option>
              </select>
              <template v-else>{{ deptName }}</template>
            </td>
          </tr>
          <tr>
            <td class="lbl">联系电话</td><td colspan="2">
              <input v-if="mode === 'edit'" v-model.trim="model.hx.leaderPhone" class="cellinput num"
                maxlength="20" placeholder="默认本人手机号,可改">
              <template v-else>{{ model.hx.leaderPhone }}</template>
            </td>
            <td class="lbl">电子邮箱</td><td colspan="3">
              <input v-if="mode === 'edit'" v-model.trim="model.hx.leaderEmail" class="cellinput" maxlength="128" placeholder="选填">
              <template v-else>{{ model.hx.leaderEmail }}</template>
            </td>
          </tr>

          <!-- ===== B 项目组成员及分工(校内/校外两段,可增删行;rowspan 随行数动态) ===== -->
          <tr>
            <td class="lbl vlbl" :rowspan="4 + innerMembers.length + outerMembers.length + (mode === 'edit' ? 2 : 0)">项目组成员及分工</td>
            <td class="grp" colspan="6">校内人员</td>
          </tr>
          <tr class="subhdr">
            <td>工号</td><td>姓名</td><td>所在单位</td><td>职称</td><td>专业</td><td>承担本项目的工作任务</td>
          </tr>
          <tr v-for="(m, i) in innerMembers" :key="'in' + i">
            <td><input v-if="mode === 'edit'" v-model.trim="m.employeeNo" class="cellinput num"
                maxlength="32" placeholder="输工号带出" @blur="lookup(m)">
              <template v-else>{{ m.employeeNo }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.name" class="cellinput" maxlength="64" placeholder="姓名">
              <template v-else>{{ m.name }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.unit" class="cellinput" maxlength="255" placeholder="所在单位/学院">
              <template v-else>{{ m.unit }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.title" class="cellinput" list="title-list" maxlength="32" placeholder="职称">
              <template v-else>{{ m.title }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.major" class="cellinput" maxlength="64" placeholder="专业">
              <template v-else>{{ m.major }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.duty" class="cellinput" maxlength="500" placeholder="承担的工作任务">
              <template v-else>{{ m.duty }}</template>
              <a v-if="mode === 'edit'" class="scr rowbtn" @click="removeRow('in', i)">✕</a></td>
          </tr>
          <tr v-if="mode === 'edit'" class="addrow"><td colspan="6"><a class="scr rowbtn" @click="addInner()">+ 添加校内人员</a></td></tr>
          <tr>
            <td class="grp" colspan="6">校外人员</td>
          </tr>
          <tr class="subhdr">
            <td colspan="2">姓名</td><td>所在单位</td><td>职务</td><td>专业</td><td>承担本项目的工作任务</td>
          </tr>
          <tr v-for="(m, i) in outerMembers" :key="'out' + i">
            <td colspan="2"><input v-if="mode === 'edit'" v-model.trim="m.name" class="cellinput" maxlength="64" placeholder="姓名">
              <template v-else>{{ m.name }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.unit" class="cellinput" maxlength="255" placeholder="所在单位">
              <template v-else>{{ m.unit }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.title" class="cellinput" list="title-list" maxlength="32" placeholder="职务">
              <template v-else>{{ m.title }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.major" class="cellinput" maxlength="64" placeholder="专业">
              <template v-else>{{ m.major }}</template></td>
            <td><input v-if="mode === 'edit'" v-model.trim="m.duty" class="cellinput" maxlength="500" placeholder="承担的工作任务">
              <template v-else>{{ m.duty }}</template>
              <a v-if="mode === 'edit'" class="scr rowbtn" @click="removeRow('out', i)">✕</a></td>
          </tr>
          <tr v-if="mode === 'edit'" class="addrow"><td colspan="6"><a class="scr rowbtn" @click="addOuter()">+ 添加校外人员</a></td></tr>

          <!-- ===== C/D 简介/进度 ===== -->
          <tr>
            <td class="lbl">项目简介</td>
            <td colspan="6">
              <AutoTextarea v-model="model.hx.projectSummary" :maxlength="800" :mode="mode"
                min-h="24mm" placeholder="项目背景/目标内容/技术路线/预期成果" @blur="touch" />
              <details v-if="mode === 'edit'" class="hint">
                <summary>示例:项目简介怎么写(建议 200-300 字,要求信息完整)</summary>
                <ul>
                  <li><b>项目背景:</b>甲方需求、行业现状、立项必要性</li>
                  <li><b>项目目标和内容:</b>交付物与服务周期</li>
                  <li><b>技术路线:</b>方法/流程/关键技术/产学研</li>
                  <li><b>预期成果:</b>量化指标</li>
                </ul>
              </details>
            </td>
          </tr>
          <tr>
            <td class="lbl">项目计划进度</td>
            <td colspan="6">
              <AutoTextarea v-model="model.hx.progressPlan" :maxlength="800" :mode="mode"
                min-h="22mm" placeholder="按时间段排布的工作计划" @blur="touch" />
              <details v-if="mode === 'edit'" class="hint">
                <summary>示例:计划进度怎么写(建议 200-300 字)</summary>
                <ul>
                  <li>X年X月-X年X月:需求调研与方案设计</li>
                  <li>X年X月-X年X月:开发与测试</li>
                  <li>X年X月-X年X月:实施联调与培训</li>
                  <li>X年X月-X年X月:验收归档</li>
                </ul>
              </details>
            </td>
          </tr>

          <!-- ===== E 经费预算 ===== -->
          <tr>
            <td class="lbl" rowspan="8">经费预算(元)</td>
            <td class="para" colspan="6" style="min-height:14mm">所填报的各项信息,内容完全真实有效。
              <span style="float:right">项目负责人(签字):&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;年&emsp;月&emsp;日</span>
              <details v-if="mode === 'edit'" class="hint">
                <summary>费用科目说明</summary>
                <ul>
                  <li><b>业务费:</b>直接支出(材料/测试/加工/出版/会议/差旅等)</li>
                  <li><b>劳务费:</b>非在编人员劳务(校内人员属绩效)</li>
                  <li><b>绩效:</b>自主确定,比例见管理办法</li>
                  <li><b>业务接待费 / 协作费:</b>协作费须合同中明确约定</li>
                  <li>按合同填写;无比例限制的科目勾选「合同约定不受限」</li>
                </ul>
              </details>
            </td>
          </tr>
          <tr v-for="b in budgetRows" :key="b.subject">
            <td>{{ b.subject }}</td>
            <td colspan="5" class="bcell">
              <template v-if="mode === 'edit'">
                <input v-if="!b.unrestricted" v-model.number="b.amount" type="number" min="0" step="0.01"
                  class="amt num" placeholder="金额(元)">
                <span v-else class="unl">合同约定不受限</span>
                <label class="scr cbx"><input type="checkbox" :checked="!!b.unrestricted"
                  @change="toggleBudget(b)">合同约定不受限</label>
              </template>
              <template v-else>
                <span v-if="b.unrestricted">合同约定不受限</span>
                <span v-else-if="b.amount" class="num">{{ money(b.amount) }}</span>
              </template>
            </td>
          </tr>
          <tr style="font-weight:bold">
            <td>预算合计</td>
            <td colspan="5" class="num"><span>{{ money(budgetTotal) }}</span>
              <span v-if="mode === 'edit' && budgetTotal !== Number(model.hx.contractAmount)"
                class="scr" style="color:#f56c6c;margin-left:8px">须等于合同经费 {{ money(model.hx.contractAmount) }} 元</span>
            </td>
          </tr>

          <!-- ===== F/G 设备场地/管理费 ===== -->
          <tr>
            <td class="lbl">设备场地</td>
            <td class="chk" colspan="6">项目是否以使用学校仪器设备和场地为主:<CheckGroup
              v-model="useSchoolAssetFlag" :options="['是', '否']" :mode="mode" /></td>
          </tr>
          <tr>
            <td class="chk" colspan="3">是否收取管理费:<CheckGroup v-model="chargeFlag"
              :options="['是', '否']" :mode="mode" /></td>
            <td colspan="4" class="num">收取管理费金额:{{ chargeFlag === '是' ? money(mgmtFee) : '' }} 元
              <span v-if="chargeFlag === '是'" class="scr">(比例换算:{{ meta.mgmtRate }} % × 合同经费
                {{ money(model.hx.contractAmount) }},自动计算;比例由科技处在系统参数中统一设定)</span>
              <div class="hint">是否收取管理费,请根据审批流程如实填写;比例由科技处统一设定,教师不可修改,金额=合同经费×比例自动换算。</div>
            </td>
          </tr>

          <!-- ===== H 意见签字区(纸面留白) ===== -->
          <tr>
            <td class="lbl">项目所在单位负责人意见</td>
            <td colspan="6" class="sign-block">
              <div class="hint">参考措辞(纸面留白手写):情况属实,同意申报。</div>
              <div class="sign-line">(签字):&emsp;&emsp;&emsp;(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
          </tr>
          <tr>
            <td class="lbl" rowspan="2">科技处意见</td>
            <td colspan="4" class="sign-block">
              <div class="sign-line">负责人(签字):(盖章)<br>年&emsp;月&emsp;日</div>
            </td>
            <td class="lbl" colspan="2">立项登记日期</td>
          </tr>
          <tr>
            <td colspan="6" class="num">{{ meta.hxRegisterDate || '' }}&emsp;&emsp;　年&emsp;月&emsp;日</td>
          </tr>
        </tbody>
      </table>

      <div class="footnote">注:本表一式四份:科技处一份,计财处一份,所在单位一份,项目负责人一份,双面打印,控制在两页内。</div>
    </div>

    <datalist id="title-list">
      <option v-for="t in meta.dictTitle" :key="t" :value="t"></option>
    </datalist>
  </div>
</template>

<!--
  登记表单据组件(演示稿 hx_register.html 转正):编辑态格内填、打印态纯文本,
  所见即所印。model 由页面壳持有并整体保存(工号 blur 自动带出/预算不受限联动/
  合计=合同经费校验/管理费只读换算);签字盖章区恒为纸面留白。
-->
<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { lookupMember } from '../../api/hx'
import { rmbCapital } from '../../utils/rmb'
import AutoTextarea from './AutoTextarea.vue'
import CheckGroup from './CheckGroup.vue'

const props = defineProps({
  model: { type: Object, required: true },   // {name,leaderName,deptId,hx{...},members[],budgets[]}
  mode: { type: String, default: 'edit' },   // edit | print
  showHints: { type: Boolean, default: true },
  meta: {
    type: Object,
    default: () => ({
      approvalNo: '', leaderPhone: '', hxRegisterDate: '', mgmtRate: null,
      dictProjectType: [], dictClientNature: [], dictTitle: [], dictBudgetSubject: [], depts: []
    })
  }
})
const emit = defineEmits(['change'])

const money = (v) => (v === null || v === undefined || v === '' ? '' : Number(v).toFixed(2))
const cnDate = (s) => {
  if (!s) return ''
  const [y, m] = String(s).slice(0, 10).split('-')
  return `${y}年${String(Number(m)).padStart(2, '0')}月`
}

const deptName = computed(() => {
  const d = (props.meta.depts || []).find((x) => x.id === props.model.deptId)
  return d ? d.name : ''
})

/* 成员两段视图:校内(教师)/校外(校外人员);行操作后 touch 通知父级防抖保存/预览 */
const innerMembers = computed(() => (props.model.members || []).filter((m) => m.memberType !== '校外人员'))
const outerMembers = computed(() => (props.model.members || []).filter((m) => m.memberType === '校外人员'))

function addInner() {
  props.model.members.push({ memberType: '教师', employeeNo: '', name: '', unit: '', title: '', major: '', duty: '' })
  touch()
}
function addOuter() {
  props.model.members.push({ memberType: '校外人员', name: '', unit: '', title: '', major: '', duty: '' })
  touch()
}
function removeRow(kind, i) {
  const list = kind === 'in' ? innerMembers.value : outerMembers.value
  props.model.members.splice(props.model.members.indexOf(list[i]), 1)
  touch()
}

/* 工号自动带出:命中填 姓名/单位/职称;查无提示手填 */
async function lookup(row) {
  if (!row.employeeNo || row.name) return
  try {
    const m = await lookupMember(row.employeeNo)
    if (m) {
      row.name = m.name || row.name
      if (m.deptName) row.unit = m.deptName
      if (m.title) row.title = m.title
    } else {
      ElMessage.info('查无此工号,请手工填写该行')
    }
  } catch (e) { /* 拦截器已提示 */ }
  touch()
}

/* 0/1 标志 ↔ 是/否 勾选组 */
const useSchoolAssetFlag = computed({
  get: () => (props.model.hx.useSchoolAsset === 1 ? '是' : props.model.hx.useSchoolAsset === 0 ? '否' : ''),
  set: (v) => { props.model.hx.useSchoolAsset = v === '是' ? 1 : 0; touch() }
})
const chargeFlag = computed({
  get: () => (props.model.hx.chargeMgmtFee === 1 ? '是' : props.model.hx.chargeMgmtFee === 0 ? '否' : ''),
  set: (v) => { props.model.hx.chargeMgmtFee = v === '是' ? 1 : 0; touch() }
})

/* 管理费=合同经费×配置比例(展示口径,落库以后端换算为准) */
const mgmtFee = computed(() => {
  const amt = Number(props.model.hx.contractAmount)
  const rate = Number(props.meta.mgmtRate)
  if (!isFinite(amt) || !isFinite(rate) || !amt) return ''
  return (amt * rate / 100).toFixed(2)
})

/* 预算:不受限联动+合计(不受限行不计入;金额归零由后端落库收口) */
function toggleBudget(b) {
  b.unrestricted = b.unrestricted ? 0 : 1
  if (b.unrestricted) b.amount = null
  touch()
}
const budgetTotal = computed(() => budgetRows.value
  .filter((b) => !b.unrestricted)
  .reduce((s, b) => s + (Number(b.amount) || 0), 0))

/**
 * 科目行恒按字典六科目渲染(版式稳定,rowspan=8 前提):
 * 编辑态=model.budgets(页面已按字典骨架初始化);打印态(审批页/打印页数据来自库中实存行,
 * 可能只存了部分科目)按字典骨架合并实存值,缺的科目显示空白——与演示稿口径一致。
 */
const budgetRows = computed(() => {
  if (props.mode === 'edit') return props.model.budgets || []
  const dict = props.meta.dictBudgetSubject || []
  if (!dict.length) return props.model.budgets || []
  return dict.map((s) => (props.model.budgets || []).find((b) => b.subject === s)
    || { subject: s, amount: null, unrestricted: 0 })
})

function touch() {
  emit('change')
}
</script>
