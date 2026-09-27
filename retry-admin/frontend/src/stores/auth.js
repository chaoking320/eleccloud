import { defineStore } from 'pinia'
import { authApi } from '@/api'

export const useAuthStore = defineStore('auth', {
  state: () => {
    let cachedUser = null
    try {
      cachedUser = JSON.parse(localStorage.getItem('eleccloud_user') || 'null')
    } catch (e) {
      cachedUser = null
    }
    return {
      token: localStorage.getItem('eleccloud_token') || '',
      user: cachedUser
    }
  },

  getters: {
    isAuthenticated: (state) => !!state.token,
    userName: (state) => state.user?.name || state.user?.username || '系统管理员'
  },

  actions: {
    async login(username, password) {
      const res = await authApi.login({ username, password })
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
          await authApi.logout()
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
