<script setup>
import {onMounted, onUnmounted, ref} from "vue"
import Semisolid from "./Semisolid.vue"
import InputBox from "./InputBox.vue"
import {useUserIdStore} from "../stores/userId.js"

const emit = defineEmits(['close'])
const userIdStore = useUserIdStore()

const identification = ref("")
const password = ref("")
const submitting = ref(false)
const errorMessage = ref("")

async function submit() {
  if (submitting.value) return
  errorMessage.value = ""

  if (!identification.value.trim() || !password.value) {
    errorMessage.value = "请输入用户名/邮箱和密码"
    return
  }

  submitting.value = true
  try {
    await userIdStore.login(identification.value.trim(), password.value)
    emit('close')
  } catch (error) {
    if (error.status === 401) {
      errorMessage.value = "用户名或密码错误"
    } else if (error.status === 429) {
      errorMessage.value = "尝试次数过多，请稍后再试"
    } else if (error.status === 503) {
      errorMessage.value = "社区服务暂时不可用，请稍后重试"
    } else {
      errorMessage.value = error.message || "登录失败，请稍后重试"
    }
    password.value = ""
  } finally {
    submitting.value = false
  }
}

function close() {
  if (!submitting.value) emit('close')
}

function onOverlayClick(e) {
  if (e.target === e.currentTarget) close()
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="modal-overlay" @click="onOverlayClick">
      <div class="modal-container" @click.stop>
        <a class="modal-close" @click="close" title="关闭">&times;</a>

        <Semisolid color="blue">
          <strong class="flex-row gap-small">
            <img src="/static/to-picbed.png" alt="SMBX World" class="img16"/>
            使用 SMBX World 社区账号登录
          </strong>
          <p>输入你在 SMBX World 论坛的用户名（或注册邮箱）和密码。</p>
        </Semisolid>

        <Semisolid color="white">
          <div class="login-form">
            <div class="flex-row gap-small">
              <label for="login-identification">用户名/邮箱：</label>
              <InputBox
                  id="login-identification"
                  v-model="identification"
                  autocomplete="username"
                  :disabled="submitting"
                  autofocus
                  :style="{ maxWidth: '220px', width: '100%' }"
                  @submit="submit"
              />
            </div>

            <div class="flex-row gap-small">
              <label for="login-password">密码：</label>
              <InputBox
                  id="login-password"
                  v-model="password"
                  type="password"
                  autocomplete="current-password"
                  :disabled="submitting"
                  :style="{ maxWidth: '220px', width: '100%' }"
                  @submit="submit"
              />
            </div>

            <div v-if="errorMessage" class="status-message error">
              {{ errorMessage }}
            </div>

            <div class="flex-row gap-small" :class="{ disabled: submitting }" @click="submit">
              <img src="/static/star.png" alt="login" class="img16"/>
              <a><strong>{{ submitting ? '登录中...' : '登录' }}</strong></a>
            </div>
          </div>
        </Semisolid>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  animation: fadeIn 0.15s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-container {
  position: relative;
  width: min(90vw, 480px);
  max-height: 85vh;
  overflow-y: auto;
  padding: 1em;
  animation: slideUp 0.2s ease-out;
}

@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.modal-close {
  position: absolute;
  top: 0.3em;
  right: 0.6em;
  font-size: 1.5em;
  line-height: 1;
  z-index: 10;
  color: #666;
  cursor: pointer;
}

.modal-close:hover {
  color: #000;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-message {
  padding: 0.5em 0.75em;
  border-radius: 6px;
  font-weight: 500;
  font-size: 0.9em;
}

.status-message.error {
  background: #f8d7da;
  color: #721c24;
}

.disabled {
  opacity: 0.5;
  pointer-events: none;
}

@media (max-width: 600px) {
  .modal-container {
    width: 100vw;
    max-height: 100vh;
    border-radius: 0;
    padding: 0.5em;
  }
  .modal-close {
    top: 0.5em;
    right: 0.8em;
  }
}
</style>
