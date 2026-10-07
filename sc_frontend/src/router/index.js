import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
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
      {
        path: 'template',
        name: 'TemplateManage',
        component: () => import('../views/platform/TemplateManage.vue'),
        meta: { title: '模板管理', roles: ['admin'] }
      }
      /* ==== 模块路由按阶段注册:N2 模板管理 / N3 教师端横向 / N4 科技处端 ==== */
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
