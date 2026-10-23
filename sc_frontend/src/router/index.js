import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  /* 打印页(HTML即交付):Layout 外新标签,只读 Sheet 自动弹打印;教师本人/admin 可访问 */
  {
    path: '/print/:flowType/:flowId',
    name: 'PrintPage',
    component: () => import('../views/PrintPage.vue'),
    meta: { title: '打印', roles: ['teacher', 'admin'] }
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/notify',
    children: [
      {
        path: 'notify',
        name: 'Notify',
        component: () => import('../views/Notify.vue'),
        meta: { title: '消息通知', roles: ['teacher', 'admin'] }
      },
      /* ==== N3 教师端横向项目(teacher+admin) ==== */
      {
        path: 'hx',
        name: 'MyProjects',
        component: () => import('../views/hx/MyProjects.vue'),
        meta: { title: '横向项目', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/register',
        name: 'RegisterPage',
        component: () => import('../views/hx/RegisterPage.vue'),
        meta: { title: '项目登记', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/register/:flowId',
        component: () => import('../views/hx/RegisterPage.vue'),
        meta: { title: '项目登记', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/change',
        name: 'ChangePage',
        component: () => import('../views/hx/ChangePage.vue'),
        meta: { title: '项目变更', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/change/:flowId',
        component: () => import('../views/hx/ChangePage.vue'),
        meta: { title: '项目变更', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/acceptance',
        name: 'AcceptancePage',
        component: () => import('../views/hx/AcceptancePage.vue'),
        meta: { title: '项目验收', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/acceptance/:flowId',
        component: () => import('../views/hx/AcceptancePage.vue'),
        meta: { title: '项目验收', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/flow/:flowId',
        name: 'FlowDetail',
        component: () => import('../views/hx/FlowDetail.vue'),
        meta: { title: '流程详情', roles: ['teacher', 'admin'] }
      },
      {
        path: 'hx/funding',
        name: 'FundingView',
        component: () => import('../views/hx/FundingView.vue'),
        meta: { title: '到账明细', roles: ['teacher', 'admin'] }
      },
      /* ==== N4 科技处端(admin 六页 Task26+备份页 Task27) ==== */
      {
        path: 'admin/audit',
        name: 'AuditList',
        component: () => import('../views/admin/AuditList.vue'),
        meta: { title: '横向审批', roles: ['admin'] }
      },
      {
        path: 'admin/audit/:flowId',
        name: 'AuditDetail',
        component: () => import('../views/admin/AuditDetail.vue'),
        meta: { title: '审批详情', roles: ['admin'] }
      },
      {
        path: 'admin/projects',
        name: 'ProjectManage',
        component: () => import('../views/admin/ProjectManage.vue'),
        meta: { title: '项目管理', roles: ['admin'] }
      },
      {
        path: 'admin/dicts',
        name: 'DictManage',
        component: () => import('../views/admin/DictManage.vue'),
        meta: { title: '字典管理', roles: ['admin'] }
      },
      {
        path: 'admin/users',
        name: 'UserManage',
        component: () => import('../views/admin/UserManage.vue'),
        meta: { title: '用户管理', roles: ['admin'] }
      },
      {
        path: 'admin/backup',
        name: 'BackupManage',
        component: () => import('../views/admin/BackupManage.vue'),
        meta: { title: '数据备份', roles: ['admin'] }
      },
      {
        path: 'admin/params',
        name: 'SysParams',
        component: () => import('../views/admin/SysParams.vue'),
        meta: { title: '系统参数', roles: ['admin'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫 + 角色守卫
router.beforeEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 科研管理平台`
    : '科研管理平台'

  const token = localStorage.getItem('sc_token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/'
  }
  // 角色限制:meta.roles 未包含当前角色则退回默认首页
  if (to.meta.roles) {
    const user = JSON.parse(localStorage.getItem('sc_user') || 'null')
    const role = user?.role || ''
    if (!to.meta.roles.includes(role)) {
      return '/'
    }
  }
})

export default router
