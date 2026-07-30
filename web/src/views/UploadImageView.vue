<script setup>
import {ref} from "vue"
import ProgressBar from "../components/ProgressBar.vue"
import UploadBox from "../components/UploadBox.vue"
import Semisolid from "../components/Semisolid.vue"
import {useUserIdStore} from "../stores/userId.js";
import {useRouterStore} from "../stores/router.js";
import {uploadFile} from "../utils.js";
import CopyUrl from "../components/CopyUrl.vue";

const percent = ref(0)
const processedChunks = ref(0)
const totalChunks = ref(0)
const file = ref(null)
const state = ref("idle") // idle, uploading, completed, error
const url = ref("")

const accept = "image/*"

const router = useRouterStore()
const userIdStore = useUserIdStore()

function handleFileSelected(f) {
  file.value = f
}

function startUpload() {
  if (file.value === null) {
    alert("请先选择一个文件")
    return
  }
  state.value = "uploading"
  percent.value = 0
  processedChunks.value = 0
  totalChunks.value = 0

  uploadFile(file.value, file.value.name,"PICBED", (p, progress) => {
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
        state.value = "completed"
      } else {
        alert("上传失败：" + data.message)
        state.value = "error"
      }
    })
}
</script>

<template>
  <div class="upload-image-view" v-if="userIdStore.userId !== -1">
    <Semisolid color="blue">
      <strong class="flex-row gap-small center"><img src="/static/to-picbed.png" alt="picbed icon" class="img16" />SMBX World 图床</strong>
      <p>本站提供免费的图片托管服务，支持主流类型的图片格式。</p>
      <p>上传的图片可以通过链接直接访问。</p>
      <strong>注意，请不要上传包含黄暴恐内容、法律法规不允许的内容和侵犯他人权益的内容的图片。</strong>
    </Semisolid>
    <Semisolid color="white">

      <div v-if="state === 'idle'">
        <UploadBox @file="handleFileSelected" :accept="accept">
          <div class="flex-row gap-small" v-if="file !== null" @click="startUpload">
            <img src="/static/star.png" alt="star icon" /><strong>开始上传！</strong>
          </div>
        </UploadBox>
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
      <strong>SMBX World 图床</strong>
      <p>请先登录后，再使用本功能。</p>
    </Semisolid>
  </div>
</template>

<style scoped>

</style>
