<script setup>
import {ref, onMounted} from "vue"
import {useUrlStore} from "../stores/url.js";
import {useUserIdStore} from "../stores/userId.js";

const avatarUrl = ref("/static/unknown.png")
const nickname = ref("点击登录")
const urlStore = useUrlStore()
const userIdStore = useUserIdStore()
const loading = ref(true)
const clickToLogin = ref(false)

onMounted(() => {
  fetch("/api/user", {redirect: "manual"}).then(res => {
    loading.value = false
    if (res.ok) {
      res.json().then(data =>{
        avatarUrl.value = data.avatar_url || "/static/unknown.png"
        nickname.value = data.nickname || "点击登录"
        userIdStore.setUserId(data.id)
        userIdStore.setAdmin(data.isAdmin || false)
      })
    } else {
      // 如果是游客，这个 api 会返回 302
      const url = res.url || res.headers.get("Location")
      if (url !== null) urlStore.setLoginUrl(url)
      nickname.value = "点击登录"
      clickToLogin.value = true
      userIdStore.clearUserId()
    }
  })
})

const login = () => {
  if (clickToLogin.value) {
    urlStore.jumpToLogin()
  }
}
</script>

<template>
  <div class="user-avatar" :class="{'click-to-login': clickToLogin}" @click="login">
    <img
        class="avatar no-drag"
        :src="avatarUrl"
        alt="头像"
        :class="{'loading': loading}"
        @error="avatarUrl = '/static/unknown.png'"
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
.loading {
  animation: loading 2s infinite;
}
@keyframes loading {
  0% {
    filter: blur(4px);
  }
  50% {
    filter: blur(2px);
  }
  100% {
    filter: blur(4px);
  }
}
.click-to-login {
  cursor: pointer;
}
</style>
