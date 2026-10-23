/* 详情 → 单据组件模型映射(只读态共用):PrintPage 与 AuditDetail 同口径,所见即所印 */
import { getConfig, listDepts, listDict } from '../api/hx'
import { toWan } from '../utils/rmb'

const cn = (v) => (v ? String(v).slice(0, 10) : '')

/**
 * @param d FlowDetailVO(flowDetail 返回)
 * @param flowType register|change|acceptance
 * @returns {Promise<{model:Object, meta:Object}>} 对应 Sheet 组件的 props
 */
export async function buildSheetModel(d, flowType) {
  const meta = { orig: {}, depts: [] }
  const o = meta.orig
  o.approvalNo = d.project.approvalNo || ''
  o.name = d.project.name
  o.leaderName = d.project.leaderName
  o.leaderPhone = d.project.leaderPhone || ''
  o.deptName = d.project.deptName || ''
  o.clientName = d.hx.clientName
  o.clientNature = d.hx.clientNature
  o.contractAmount = d.hx.contractAmount
  o.startDate = cn(d.hx.startDate)
  o.endDate = cn(d.hx.endDate)

  if (flowType === 'register') {
    const [pt, cn2, bs, ds] = await Promise.all([
      listDict('hx_project_type'), listDict('hx_client_nature'),
      listDict('hx_budget_subject'), listDepts()
    ])
    meta.depts = ds
    meta.dictProjectType = pt.map((x) => x.label)
    meta.dictClientNature = cn2.map((x) => x.label)
    meta.dictBudgetSubject = bs.map((x) => x.label)
    meta.hxRegisterDate = d.hx.hxRegisterDate || ''
    meta.leaderPhone = o.leaderPhone
    meta.mgmtRate = await getConfig('hx_mgmt_fee_rate')
    return {
      meta,
      model: {
        name: d.project.name,
        leaderName: d.project.leaderName,
        deptId: d.project.deptId,
        hx: { ...d.hx, startDate: o.startDate, endDate: o.endDate },
        members: (d.members || []).map((m) => ({ ...m })),
        budgets: (d.budgets || []).map((b) => ({ ...b }))
      }
    }
  }

  if (flowType === 'change') {
    const ct = await listDict('hx_change_type')
    meta.dictChangeType = ct.map((x) => x.label)
    meta.dictProjectType = []
    return {
      meta,
      model: {
        projectType: d.hx.projectType,
        changeTypes: (d.flow.changeType || '').split(',').filter(Boolean),
        changeContent: d.flow.changeContent || '',
        changeReason: d.flow.changeReason || '',
        newAmount: d.flow.newAmount,
        newEndDate: cn(d.flow.newEndDate),
        newLeaderId: null
      }
    }
  }

  const [of, pt, cn2] = await Promise.all([
    listDict('hx_outcome_form'), listDict('hx_project_type'), listDict('hx_client_nature')
  ])
  meta.dictOutcomeForm = of.map((x) => x.label)
  meta.dictProjectType = pt.map((x) => x.label)
  meta.dictClientNature = cn2.map((x) => x.label)
  meta.receivedTotal = d.receivedTotal ?? null
  meta.verifiedAmountWan = d.hx.verifiedAmount ? toWan(d.hx.verifiedAmount) : ''
  const verify = (d.records || []).filter((x) => x.action === 'verify').pop()
  meta.verifierName = verify ? verify.operatorName || '' : ''
  return {
    meta,
    model: {
      projectType: d.hx.projectType,
      clientNature: d.hx.clientNature,
      usedAmount: d.flow.usedAmount,
      finishDate: cn(d.flow.finishDate),
      isOnTime: d.flow.isOnTime,
      outcomeForms: (d.flow.outcomeForm || '').split(',').filter(Boolean),
      acceptSummary: d.flow.acceptSummary || '',
      reportContent: d.flow.reportContent || '',
      members: (d.members || []).filter((m) => m.memberType !== '学生').map((m) => ({ ...m })),
      students: (d.members || []).filter((m) => m.memberType === '学生').map((m) => ({ ...m }))
    }
  }
}

/**
 * Sheet model → 各流程保存 DTO(admin 代改提交用;与教师端 collectForm 同口径):
 * 成员过滤空名+连续 sortNo、预算过滤空金额(不受限行保留)、多选 join。
 */
export function collectSheetDto(flowType, model, flowId, projectId) {
  if (flowType === 'register') {
    const members = (model.members || [])
      .filter((m) => (m.name || '').trim())
      .map((m, i) => ({ ...m, name: m.name.trim(), sortNo: i + 1 }))
    const budgets = (model.budgets || [])
      .filter((b) => b.unrestricted === 1 || (b.amount !== null && b.amount !== undefined && b.amount !== ''))
      .map((b) => ({ subject: b.subject, amount: b.amount, unrestricted: b.unrestricted || 0 }))
    return {
      flowId, name: model.name, leaderName: model.leaderName, deptId: model.deptId,
      hx: { ...model.hx }, members, budgets
    }
  }
  if (flowType === 'change') {
    const has = (t) => (model.changeTypes || []).includes(t)
    return {
      flowId, projectId,
      changeType: (model.changeTypes || []).join(','),
      changeContent: model.changeContent || '',
      changeReason: model.changeReason || '',
      newAmount: has('项目经费') ? model.newAmount : null,
      newEndDate: has('执行周期') ? model.newEndDate : null,
      newLeaderId: has('项目负责人') ? model.newLeaderId : null
    }
  }
  const members = (model.members || [])
    .filter((m) => (m.name || '').trim())
    .map((m, i) => ({ ...m, name: m.name.trim(), sortNo: i + 1 }))
  const students = (model.students || [])
    .filter((s) => (s.name || '').trim())
    .map((s, i) => ({ ...s, memberType: '学生', name: s.name.trim(), sortNo: members.length + i + 1 }))
  return {
    flowId, projectId,
    usedAmount: model.usedAmount,
    finishDate: model.finishDate || null,
    isOnTime: model.isOnTime,
    outcomeForm: (model.outcomeForms || []).join(','),
    acceptSummary: model.acceptSummary || '',
    reportContent: model.reportContent || '',
    members: [...members, ...students]
  }
}
