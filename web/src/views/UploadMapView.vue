<script setup>

import {computed, onMounted, ref} from "vue";
import Semisolid from "../components/Semisolid.vue";
import {useUserIdStore} from "../stores/userId.js";
import ProgressBar from "../components/ProgressBar.vue";
import InputBox from "../components/InputBox.vue";
import {GAME_VERSION_INFO, getXsrfToken, uploadFile} from "../utils.js";
import UploadBox from "../components/UploadBox.vue";
import CopyUrl from "../components/CopyUrl.vue";
import {useRouterStore} from "../stores/router.js";

// refs
const title = ref("")
const version = ref("")
const author = ref("")
const file = ref(null)  // selected file

const url = ref("") // upload URL

const state = ref("idle") // idle, uploading, completed, error
const percent = ref(0)
const processedChunks = ref(0)
const totalChunks = ref(0)
const versions = ref([])  // available versions

// stores
const router = useRouterStore()
const userIdStore = useUserIdStore()

function initVersions() {
  if (versions.value.length > 0) return
  fetch("/api/versions")
      .then(res => res.json())
      .then(j => {
        versions.value = j.data.filter(v => v !== 'UNKNOWN')
      })
      .catch(err => {
        console.error("Failed to fetch versions:", err)
      })
}

onMounted(() => {
  initVersions()
})

function setFile(f) {
  file.value = f
}

const isValid = computed(() => {
  return title.value.trim() !== "" && version.value !== "" && author.value.trim() !== "" && file.value !== null
})

const body = url => ({
  title: title.value,
  uploader: userIdStore.userId,
  author: author.value,
  gameVersion: version.value,
  downloadProvider: "DIRECT_LINK",
  downloadUrl: url,
})

function startUpload() {
  if (!isValid.value) {
    alert("请先填写完整信息，并选择一个文件")
    return
  }
  const ext = file.value.name.split('.').pop()
  const filename = '[' + GAME_VERSION_INFO[version.value].short + '] ' +  title.value.trim() + (ext ? ('.' + ext) : '')
  state.value = "uploading"
  percent.value = 0
  processedChunks.value = 0
  totalChunks.value = 0

  uploadFile(file.value, filename, "WORLDMAP", (p, progress) => {
    percent.value = p
    if (progress) {
      processedChunks.value = Math.max(processedChunks.value, progress.savedChunkCount)
      totalChunks.value = progress.totalChunks
    }
  })
      .then(response => response.json())
      .then(data => {
        if (data.code === 0) {
          url.value = data.data
          file.value = null

          // 上传基本信息
          fetch("/api/worldmaps", {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'X-XSRF-TOKEN': getXsrfToken()
            },
            body: JSON.stringify(body(data.data)),
          }).then(res => res.json())
            .then(infoData => {
              if (infoData.code === 0) {
                state.value = "completed"
              } else {
                alert("上传地图信息失败：" + infoData.message)
                state.value = "error"
              }
            })
        } else {
          alert("上传失败：" + data.message)
          state.value = "error"
        }
      })
}

</script>

<template>
  <div class="upload-worldmap-view" v-if="userIdStore.userId !== -1">
    <Semisolid color="blue">
      <strong class="flex-row gap-small center"><img src="/static/to-upload.png" alt="upload icon"
                                                     class="img16"/>上传地图</strong>
      <p>在 SMBX World 投稿地图时，可在此上传地图文件。<span
          class="inline-block">上传后会生成一个链接，可在帖子中使用该链接。</span></p>
      <p>注意：本站提供的免费文件托管服务<u>不具有任何可靠性保障</u>，<span
          class="inline-block">故请在投稿地图时，也请将地图文件上传到其他可靠的商业网盘或托管服务。</span></p>
    </Semisolid>

    <Semisolid color="white">
      <div class="upload-worldmap-container" v-if="state === 'idle'">

        <div class="upload-worldmap-details">
          <strong>地图基本信息</strong>
          <p class="flex-row">
            请核对后，仔细填写下列信息。
          </p>
          <div class="flex-row gap-small">
            <img src="/static/qblock.gif" alt="question block" class="img16"/>
            <label for="title">地图名称：</label>
            <InputBox id="title" v-model="title" :style="{ maxWidth: '200px', width: '100%' }"/>
          </div>
          <div class="flex-row gap-small">
            <img :src="GAME_VERSION_INFO[version]?.icon || '/static/unknown.png'" alt="version icon" class="img16"/>
            <label for="version">地图版本：</label>
            <select id="version" v-model="version" :style="{ maxWidth: '200px', width: '100%' }">
              <option v-for="v in versions" :key="v" :value="v">{{ GAME_VERSION_INFO[v]?.name || v }}</option>
            </select>
          </div>
          <div class="flex-row gap-small">
            <img src="/static/walk.gif" alt="author icon" class="img16"/>
            <label for="author">作者名称：</label>
            <InputBox id="author" v-model="author" :style="{ maxWidth: '200px', width: '100%' }"/>
          </div>

          <div v-if="isValid" class="upload-worldmap-details">
            <div class="flex-row gap-small" v-if="file !== null" @click="startUpload">
              <img src="/static/star.png" alt="star icon" /><a><strong>填写完毕，开始上传！</strong></a>
            </div>
          </div>
        </div>


        <div class="upload-worldmap-details">
          <strong>上传文件</strong>
          <UploadBox accept=".exe,.zip,.7z,.rar,.tar.gz,.tar.zstd" @file="setFile" />
        </div>
      </div>

      <div v-else-if="state === 'uploading'">
        <ProgressBar :percent="percent" />
        <br>
        <strong v-if="percent < 80">上传中，请耐心等待，不要离开或刷新此页面。</strong>
        <strong v-else>上传完成，服务器正在处理文件（{{ processedChunks }}/{{ totalChunks }} 个分块），请耐心等待。</strong>
      </div>

      <div v-else-if="state === 'completed'">
        <CopyUrl :url="url" />
        <br>
        <button @click="state = 'idle'">继续上传</button>
      </div>

      <div v-else-if="state === 'error'">
        <strong style="color: red;">上传失败，请重试。</strong>
        <br>
        <button @click="state = 'idle'">重试</button>
      </div>


      <a class="flex-row gap-small center" @click="router.push('/')"><img src="/static/direct-link.png" alt="pipe icon" class="img16" />返回首页</a>
    </Semisolid>
  </div>
  <div class="upload-image-view" v-else>
    <Semisolid color="blue">
      <strong>上传地图</strong>
      <p>请先登录后，再使用本功能。</p>
    </Semisolid>
  </div>
</template>

<style scoped>
.inline-block {
  display: inline-block;
}

.upload-worldmap-details {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-worldmap-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1em;
}


@media (max-width: 600px) {
  .upload-worldmap-container {
    grid-template-columns: 1fr;
  }
}


</style>
