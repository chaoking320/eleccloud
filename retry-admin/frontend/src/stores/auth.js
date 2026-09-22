import { defineStore } from 'pinia'
import axios from 'axios'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('eleccloud_token') || '',
    user: JSON.parse(localStorage.getItem('eleccloud_user') || 'null')
  }),

  getters: {
    isAuthenticated: (state) => !!state.token,
    userName: (state) => state.user?.name || state.user?.username || '管理员'
  },

  actions: {
    async login(username, password) {
      const response = await axios.post('/api/auth/login', { username, password })
      const res = response.data
      if (res && res.success) {
        this.token = res.data.token
        this.user = res.data
        localStorage.setItem('eleccloud_token', this.token)
        localStorage.setItem('eleccloud_user', JSON.stringify(this.user))
        return res.data
      } else {
        throw new Error(res?.message || '登录失败')
      }
    },

    async logout() {
      try {
        if (this.token) {
          await axios.post('/api/auth/logout', {}, {
            headers: { Authorization: `Bearer ${this.token}` }
          })
        }
      } catch (e) {
        // ignore logout error
      } finally {
        this.token = ''
        this.user = null
        localStorage.removeItem('eleccloud_token')
        localStorage.removeItem('eleccloud_user')
      }
    }
  }
})
