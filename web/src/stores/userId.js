import {defineStore} from "pinia";
import {ensureXsrfToken, requestJson} from "../utils.js";

export const useUserIdStore = defineStore('user_id', {
  state: () => ({
    userId: -1,  // -1 表示未登录
    username: '',
    nickname: '',
    avatarUrl: null,
    isAdmin: false,
    loaded: false,
    loginOpen: false,
  }),
  getters: {
    getUserId: (state) => {
      return state.userId
    },
    isLoggedIn: (state) => {
      return state.userId !== -1
    },
  },
  actions: {
    setUser(user) {
      this.userId = user.id
      this.username = user.username || ''
      this.nickname = user.nickname || user.username || ''
      this.avatarUrl = user.avatar_url || null
      this.isAdmin = !!user.isAdmin
      this.loaded = true
    },
    clearUser() {
      this.userId = -1
      this.username = ''
      this.nickname = ''
      this.avatarUrl = null
      this.isAdmin = false
      this.loaded = true
    },
    async fetchUser() {
      try {
        const response = await fetch('/api/user', {credentials: 'same-origin'})
        if (response.ok) {
          this.setUser(await response.json())
        } else {
          this.clearUser()
        }
      } catch (_) {
        this.clearUser()
      }
    },
    async login(identification, password) {
      const user = await requestJson('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({identification, password}),
      })
      this.setUser(user)
      this.loginOpen = false
      return user
    },
    async logout() {
      const xsrfToken = await ensureXsrfToken()
      const response = await fetch('/api/logout', {
        method: 'POST',
        credentials: 'same-origin',
        headers: xsrfToken ? {'X-XSRF-TOKEN': xsrfToken} : {},
      })
      if (!response.ok) {
        throw new Error('退出登录失败，请稍后重试')
      }
      this.clearUser()
      this.loginOpen = false
    },
    openLogin() {
      this.loginOpen = true
    },
    closeLogin() {
      this.loginOpen = false
    },
  },
})
