<script setup>
import {computed, ref, watch} from "vue"
import {useUserIdStore} from "../stores/userId.js";

const userIdStore = useUserIdStore()
const avatarFailed = ref(false)

const avatarUrl = computed(() => {
  return (avatarFailed.value || !userIdStore.avatarUrl)
    ? "/static/unknown.png"
    : userIdStore.avatarUrl
})

const nickname = computed(() => {
  if (userIdStore.nickname) return userIdStore.nickname
  if (userIdStore.isLoggedIn) return userIdStore.username
  return userIdStore.loaded ? "点击登录" : "加载中..."
})

const clickToLogin = computed(() => !userIdStore.isLoggedIn)

watch(() => userIdStore.avatarUrl, () => {
  avatarFailed.value = false
})

function onClick() {
  if (!userIdStore.isLoggedIn) {
    userIdStore.openLogin()
  }
}
</script>

<template>
  <div class="user-avatar" :class="{'click-to-login': clickToLogin}" @click="onClick">
    <img
        class="avatar no-drag"
        :src="avatarUrl"
        alt="头像"
        @error="avatarFailed = true"
        v-tooltip.left="nickname"
    />
  </div>
</template>

<style scoped>
.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: #f0f0f0;
  border: 2px solid #f0f0f0;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
}
@media (max-width: 600px) {
  .avatar, .user-avatar {
    width: 40px;
    height: 40px;
  }
}
.click-to-login {
  cursor: pointer;
}
</style>
