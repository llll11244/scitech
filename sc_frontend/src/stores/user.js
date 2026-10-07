import { defineStore } from 'pinia'
import { login as loginApi } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('sc_token') || '',
    userInfo: JSON.parse(localStorage.getItem('sc_user') || 'null')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.userInfo?.role === 'admin',
    isTeacher: (state) => state.userInfo?.role === 'teacher'
  },

  actions: {
    async login(form) {
      const res = await loginApi(form)
      this.token = res.token
      this.userInfo = {
        username: res.username,
        realName: res.realName,
        role: res.role
      }
      localStorage.setItem('sc_token', this.token)
      localStorage.setItem('sc_user', JSON.stringify(this.userInfo))
    },

    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('sc_token')
      localStorage.removeItem('sc_user')
    }
  }
})
