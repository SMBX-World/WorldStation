<script setup>
import {computed, onMounted, onUnmounted, ref, watch} from "vue"
import Semisolid from "./Semisolid.vue"
import InputBox from "./InputBox.vue"
import {GAME_VERSION_INFO, getXsrfToken} from "../utils.js"
import {useUserIdStore} from "../stores/userId.js"

const props = defineProps({
  mapId: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['close', 'updated', 'deleted'])

const userIdStore = useUserIdStore()

const loading = ref(false)
const submitting = ref(false)
const map = ref(null)
const title = ref("")
const version = ref("")
const author = ref("")
const versions = ref([])
const statusMessage = ref("")
const statusType = ref("")

const isOwnMap = computed(() => map.value?.uploader === userIdStore.userId)
const isValid = computed(() => (title.value || "").trim() !== "" && version.value !== "" && (author.value || "").trim() !== "")
const hasPermission = computed(() => map.value && (map.value.uploader === userIdStore.userId || userIdStore.isAdmin))

function resetState() {
  loading.value = true
  submitting.value = false
  map.value = null
  title.value = ""
  version.value = ""
  author.value = ""
  statusMessage.value = ""
  statusType.value = ""
}

function initVersions() {
  if (versions.value.length > 0) return
  fetch("/api/versions")
    .then(res => res.json())
    .then(j => {
      versions.value = j.data.filter(v => v !== 'UNKNOWN')
    })
}

function initMap(id) {
  resetState()
  fetch('/api/worldmaps/worldmap/' + id, {
    headers: { 'X-XSRF-TOKEN': getXsrfToken() },
  })
    .then(res => res.json())
    .then(j => {
      if (j.code === 0) {
        map.value = j.data
        title.value = map.value.title || ""
        version.value = map.value.gameVersion || ""
        author.value = map.value.author || ""
      } else {
        map.value = null
      }
      loading.value = false
    })
    .catch(() => {
      map.value = null
      loading.value = false
    })
}

function body() {
  return {
    id: map.value.id,
    title: title.value,
    uploader: map.value.uploader,
    author: author.value,
    gameVersion: version.value,
    downloadProvider: map.value.downloadProvider,
    downloadUrl: map.value.downloadUrl,
  }
}

function apply() {
  if (!isValid.value) return
  submitting.value = true
  statusMessage.value = ""
  fetch('/api/worldmaps', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': getXsrfToken(),
    },
    body: JSON.stringify(body()),
  })
    .then(res => res.json())
    .then(j => {
      submitting.value = false
      if (j.code === 0) {
        statusMessage.value = "修改成功！"
        statusType.value = "success"
        const updatedMap = {
          ...map.value,
          title: title.value,
          author: author.value,
          gameVersion: version.value,
        }
        emit('updated', updatedMap)
        setTimeout(() => emit('close'), 800)
      } else {
        statusMessage.value = "修改失败：" + j.message
        statusType.value = "error"
      }
    })
    .catch(() => {
      submitting.value = false
      statusMessage.value = "修改失败，请稍后重试"
      statusType.value = "error"
    })
}

function deleteMap() {
  if (!confirm("真的要删除 " + map.value.title + " 吗？\n该地图的下载链接将会永久不可用！（真的很久！）")) return
  submitting.value = true
  statusMessage.value = ""
  fetch('/api/worldmaps/worldmap/' + map.value.id, {
    method: 'DELETE',
    headers: { 'X-XSRF-TOKEN': getXsrfToken() },
  })
    .then(res => res.json())
    .then(j => {
      submitting.value = false
      if (j.code === 0) {
        statusMessage.value = "地图已删除！"
        statusType.value = "success"
        emit('deleted', map.value.id)
        setTimeout(() => emit('close'), 800)
      } else {
        statusMessage.value = "删除失败：" + j.message
        statusType.value = "error"
      }
    })
    .catch(() => {
      submitting.value = false
      statusMessage.value = "删除失败，请稍后重试"
      statusType.value = "error"
    })
}

function onOverlayClick(e) {
  if (e.target === e.currentTarget) emit('close')
}

function onKeydown(e) {
  if (e.key === 'Escape' && !submitting.value) emit('close')
}

watch(() => props.mapId, (newId) => {
  if (newId) {
    initVersions()
    initMap(newId)
  }
}, { immediate: true })

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="modal-overlay" @click="onOverlayClick">
      <div class="modal-container" @click.stop>
        <!-- 关闭按钮 -->
        <a class="modal-close" @click="emit('close')" title="关闭">&times;</a>

        <!-- 加载状态 -->
        <div v-if="loading" class="modal-loading">
          <Semisolid color="white">
            <strong class="flex-row gap-small center">
              <img src="/static/walk.gif" alt="loading" class="img16"/>加载中...
            </strong>
            <p>正在加载地图信息，请稍候。</p>
          </Semisolid>
        </div>

        <!-- 无权限 / 未找到 -->
        <div v-else-if="map === null || !hasPermission">
          <Semisolid color="blue">
            <strong class="flex-row gap-small center">
              <img src="/static/apply-changed.png" alt="error" class="img16"/>无法编辑地图信息
            </strong>
            <p v-if="userIdStore.userId === -1">你尚未登录，请先登录后再尝试。</p>
            <p v-else>你无权编辑此地图，或地图不存在 / 已被删除。</p>
          </Semisolid>
        </div>

        <!-- 编辑表单 -->
        <div v-else>
          <Semisolid color="blue">
            <template v-if="isOwnMap">
              <strong class="flex-row gap-small center">
                <img src="/static/qblock.gif" alt="edit" class="img16"/>编辑地图信息
              </strong>
              <p>这里可以编辑你上传的地图的信息。</p>
              <p>如果需要更换地图文件，请删除当前地图并重新上传。</p>
            </template>
            <template v-else>
              <strong class="flex-row gap-small center">
                <img src="/static/qblock.gif" alt="edit" class="img16"/>管理员编辑
              </strong>
              <p>你正在以管理员身份编辑 <strong>{{ map.title }}</strong>（上传者 ID: {{ map.uploader }}）的信息。</p>
              <p>如需更换地图文件，请删除当前地图并联系上传者重新上传。</p>
            </template>
          </Semisolid>

          <Semisolid color="white">
            <div class="upload-worldmap-details">
              <strong>地图基本信息</strong>
              <p class="flex-row">请核对后，仔细填写下列信息。</p>

              <div class="flex-row gap-small">
                <img src="/static/qblock.gif" alt="question block" class="img16"/>
                <label>地图名称：</label>
                <InputBox v-model="title" :style="{ maxWidth: '200px', width: '100%' }"/>
              </div>
              <div class="flex-row gap-small">
                <img :src="GAME_VERSION_INFO[version]?.icon || '/static/unknown.png'" alt="version" class="img16"/>
                <label>地图版本：</label>
                <select v-model="version" :style="{ maxWidth: '200px', width: '100%' }">
                  <option v-for="v in versions" :key="v" :value="v">{{ GAME_VERSION_INFO[v]?.name || v }}</option>
                </select>
              </div>
              <div class="flex-row gap-small">
                <img src="/static/walk.gif" alt="author" class="img16"/>
                <label>作者名称：</label>
                <InputBox v-model="author" :style="{ maxWidth: '200px', width: '100%' }"/>
              </div>

              <!-- 操作反馈消息 -->
              <div v-if="statusMessage" :class="['status-message', statusType]">
                {{ statusMessage }}
              </div>

              <div class="flex-row gap">
                <div class="flex-row gap-small" :class="{ disabled: !isValid || submitting }" @click="apply">
                  <img src="/static/star.png" alt="star" class="img16"/>
                  <a><strong>{{ submitting ? '提交中...' : '应用修改' }}</strong></a>
                </div>
                <div class="flex-row gap-small" @click="deleteMap">
                  <img src="/static/koopa.gif" alt="delete" style="height: 16px"/>
                  <a><strong>删除地图</strong></a>
                </div>
              </div>
            </div>
          </Semisolid>
        </div>
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
  width: min(90vw, 560px);
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

.modal-loading {
  text-align: center;
}

.upload-worldmap-details {
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
.status-message.success {
  background: #d4edda;
  color: #155724;
}
.status-message.error {
  background: #f8d7da;
  color: #721c24;
}

.disabled {
  opacity: 0.5;
  pointer-events: none;
}

/* 响应式：移动设备上全宽显示 */
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
