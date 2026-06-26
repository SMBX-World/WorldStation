<script setup>
import {ref, onMounted, onUnmounted} from "vue"
import {useUrlStore} from "../stores/url.js";
import {useUserIdStore} from "../stores/userId.js";

const avatarUrl = ref("/static/unknown.png")
const nickname = ref("点击登录")
const urlStore = useUrlStore()
const userIdStore = useUserIdStore()
const loading = ref(true)
const clickToLogin = ref(false)

/**
 * 获取当前用户信息。
 * 返回 true 表示已登录，false 表示未登录或请求失败。
 */
async function fetchUser() {
  try {
    const res = await fetch("/api/user", {redirect: "manual"})
    loading.value = false
    if (res.ok) {
      const data = await res.json()
      avatarUrl.value = data.avatar_url || "/static/unknown.png"
      nickname.value = data.nickname || "点击登录"
      userIdStore.setUserId(data.id)
      userIdStore.setAdmin(data.isAdmin || false)
      clickToLogin.value = false
      return true
    } else {
      // 302 表示未登录
      const url = res.url || res.headers.get("Location")
      if (url !== null) urlStore.setLoginUrl(url)
      nickname.value = "点击登录"
      clickToLogin.value = true
      userIdStore.clearUserId()
      return false
    }
  } catch {
    // 网络错误等瞬态故障：不清除现有登录状态，保持 UI 不变
    loading.value = false
    return false
  }
}

/**
 * 页面可见性变化时的会话恢复检查。
 * 用户切换回标签页时验证会话是否仍然有效。
 * 在容器重启/进程恢复后，由 Redis 中的持久化会话保证登录状态不丢失。
 */
function onVisibilityChange() {
  if (document.visibilityState === "visible" && userIdStore.isLoggedIn) {
    fetchUser()
  }
}

onMounted(() => {
  fetchUser()
  document.addEventListener("visibilitychange", onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener("visibilitychange", onVisibilityChange)
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
