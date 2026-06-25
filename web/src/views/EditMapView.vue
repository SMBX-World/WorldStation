<script setup>
import {computed, onMounted, ref} from "vue";
import {useRouterStore} from "../stores/router.js";
import {useUserIdStore} from "../stores/userId.js";
import Semisolid from "../components/Semisolid.vue";
import {GAME_VERSION_INFO, getXsrfToken} from "../utils.js";
import InputBox from "../components/InputBox.vue";

// refs
const loading = ref(true)
const map = ref(null)
const title = ref("")
const version = ref("")
const author = ref("")
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

function initMap() {
  const id = new URL(router.route, window.location.origin).searchParams.get("id")
  if (!id) {
    map.value = null
    loading.value = false
    return
  }
  fetch('/api/worldmaps/worldmap/' + id, {
    method: 'GET',
    headers: {
      'X-XSRF-TOKEN': getXsrfToken(),
    },
  }).then(res => res.json())
      .then(j => {
        if (j.code === 0) {
          map.value = j.data
          title.value = map.value.title
          version.value = map.value.gameVersion
          author.value = map.value.author
          console.log("Loaded map info:", map.value)
        } else {
          map.value = null
        }
        loading.value = false
      })
      .catch(err => {
        console.error("Failed to fetch map info:", err)
        map.value = null
        loading.value = false
      })
}

const isValid = computed(() => {
  return title.value.trim() !== "" && version.value !== "" && author.value.trim() !== ""
})

onMounted(() => {
  initVersions()
  initMap()
})

const body = () => ({
  id: map.value.id,
  title: title.value,
  uploader: map.value.uploader,
  author: author.value,
  gameVersion: version.value,
  downloadProvider: map.value.downloadProvider,
  downloadUrl: map.value.downloadUrl,
})

function apply() {
  if (!isValid.value) {
    alert("请先填写完整信息")
    return
  }
  fetch('/api/worldmaps', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': getXsrfToken(),
    },
    body: JSON.stringify(body()),
  }).then(res => res.json())
      .then(j => {
        if (j.code === 0) {
          alert("修改成功！")
          router.push('/')
        } else {
          alert("修改失败：" + j.message)
        }
      })
      .catch(err => {
        console.error("Failed to apply changes:", err)
        alert("修改失败，请稍后重试")
      })
}

function deleteMap() {
  if (!confirm("真的要删除 " + map.value.title + " 吗？\n该地图的下载链接将会永久不可用！（真的很久！）")) {
    return
  }
  fetch('/api/worldmaps/worldmap/' + map.value.id, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': getXsrfToken(),
    },
  }).then(res => res.json())
      .then(j => {
        if (j.code === 0) {
          alert("地图已删除！")
          router.push('/')
        } else {
          alert("删除失败：" + j.message)
        }
      })
      .catch(err => {
        console.error("Failed to delete map:", err)
        alert("删除失败，请稍后重试")
      })
}

</script>

<template>
  <div class="edit-map-view" v-if="!loading && map !== null && map.uploader === userIdStore.userId">
    <Semisolid color="blue">
      <strong class="flex-row gap-small center"><img src="/static/qblock.gif" alt="edit icon"
                                                     class="img16"/>编辑地图信息</strong>
      <p>这里可以编辑你上传的地图的信息。</p>
      <p>如果需要更换地图文件，请删除当前地图并重新上传。</p>
    </Semisolid>
    <Semisolid color="white">
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

        <div class="flex-row gap">
          <div class="flex-row gap-small" v-if="isValid" @click="apply">
            <img src="/static/star.png" alt="star icon" class="img16"/><a><strong>应用修改</strong></a>
          </div>
          <div class="flex-row gap-small" @click="deleteMap">
            <img src="/static/koopa.gif" alt="koopa troopa icon" style="height: 16px"/><a><strong>删除地图</strong></a>
          </div>
        </div>

      </div>
      <a class="flex-row gap-small center" @click="router.push('/')"><img src="/static/direct-link.png" alt="pipe icon" class="img16" />返回首页</a>

    </Semisolid>
  </div>
  <div v-else-if="loading">
    <Semisolid color="white">
      <strong class="flex-row gap-small center"><img src="/static/walk.gif" alt="loading icon"
                                                     class="img16"/>加载中...</strong>
      <p>正在加载地图信息，请稍候。</p>
    </Semisolid>
  </div>
  <div v-else-if="map === null">
    <Semisolid color="blue">
      <strong class="flex-row gap-small center"><img src="/static/apply-changed.png" alt="error icon" class="img16"/>无法编辑地图信息</strong>
      <p>未找到指定的地图，请检查链接是否正确。</p>
    </Semisolid>
  </div>
  <div v-else>
    <Semisolid color="red">
      <strong class="flex-row gap-small center"><img src="/static/apply-changed.png" alt="error icon" class="img16"/>无法编辑地图信息</strong>
      <p v-if="userIdStore.userId === -1">你尚未登录，请先登录后再尝试。</p>
      <p v-else>你无权编辑此地图的信息，可能的原因包括但不限于：<br/>1. 该地图并非由你上传；<br/>2. 地图不存在或已被删除。
      </p>
    </Semisolid>
  </div>
</template>

<style scoped>

.upload-worldmap-details {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
