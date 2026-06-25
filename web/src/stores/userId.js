import {defineStore} from "pinia";

export const useUserIdStore = defineStore('user_id', {
  state: () => ({
    userId: -1,  // unset
    isAdmin: false,
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
    setUserId(userId) {
      this.userId = userId
    },
    setAdmin(isAdmin) {
      this.isAdmin = isAdmin
    },
    clearUserId() {
      this.userId = -1
      this.isAdmin = false
    },
  },
})
