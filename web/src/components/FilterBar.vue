<script setup>
import Semisolid from "./Semisolid.vue"
import InputBox from "./InputBox.vue"
import {onMounted, ref} from "vue"
import {GAME_VERSION_INFO} from "../utils.js";
import {useUserIdStore} from "../stores/userId.js";
import {useRouterStore} from "../stores/router.js";

// props
defineProps({
  isLoggedIn: {
    type: Boolean,
    default: false
  }
})

// refs
const folded = ref(false)
const versions = ref([])  // available versions
const changed = ref(false) // whether the filter has changed and not applied yet
// refs - filters
const title = ref("")
const version = ref("")
const onlyUploader = ref(false)
const sort = ref("")

const userIdStore = useUserIdStore()
const router = useRouterStore()
const emit = defineEmits(['applyFilter'])

onMounted(() => {
  if (window.innerWidth < 600) {
    fold()
  } else {
    unfold()
  }
})

function fold() {
  folded.value = true
}

function unfold() {
  folded.value = false
  initVersions()
}

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

function applyFilter() {
  changed.value = false
  emit('applyFilter', title.value, version.value, onlyUploader.value ? userIdStore.userId : -1, sort.value)
}

function setChanged() {
  changed.value = true
}

</script>

<template>
  <Semisolid color="white" class="filter-bar">
    <div class="flex-row gap">
      <a v-if="isLoggedIn" class="flex-row gap-small" @click="router.push('/upload/worldmap')">
        <img class="img16" src="/static/to-upload.png" alt="上传地图"/>
        <strong>上传地图</strong>
      </a>
      <a v-if="isLoggedIn" class="flex-row gap-small" @click="router.push('/upload/image')">
        <img class="img16" src="/static/to-picbed.png" alt="上传图片"/>
        <strong>上传图片</strong>
      </a>
      <span v-if="isLoggedIn">|</span>
      <span class="flex-row gap-small">
        <img class="img16" src="/static/pipe.png" alt="筛选"/>
        <strong>筛选地图 ...</strong>
      </span>
      <span class="flex-right" v-if="folded" @click="unfold">展开 ▼</span>
      <span class="flex-right" v-else @click="fold">收起 ▲</span>
    </div>
    <div v-if="!folded">
      <hr/>
      <div class="flex-row gap">
        <span class="flex-row gap-small">
          <strong>标题</strong>
          <InputBox v-model="title" @submit="applyFilter" @input="setChanged"/>
        </span>
        <span class="flex-row gap-small">
          <strong>版本</strong>
          <img class="img16" :src="GAME_VERSION_INFO[version].icon" v-if="version" alt="版本图标"/>
          <select v-model="version" @change="setChanged">
            <option value="">全部版本</option>
            <option v-for="v in versions" :key="v" :value="v">{{ GAME_VERSION_INFO[v].name }}</option>
          </select>
        </span>
        <span class="flex-row gap-small">
          <strong>排序</strong>
          <select v-model="sort" @change="setChanged">
            <option value="">默认排序</option>
            <option value="title">按作品名称排序</option>
          </select>
        </span>
        <span v-if="isLoggedIn">
          <input type="checkbox" v-model="onlyUploader" id="onlyUploader" @change="setChanged"/>
          <label for="onlyUploader">仅显示我上传的地图</label>
        </span>
        <a class="flex-row flex-right gap-small" @click="applyFilter">
          <img class="img16" :src="changed ? '/static/apply-changed.png' : '/static/apply.png'" alt="应用筛选"/>
          <span :class="changed ? 'underscore' : ''">应用筛选</span>
        </a>
      </div>
    </div>
  </Semisolid>
</template>

<style scoped>
.filter-bar {
  transition: height 0.3s ease-in-out;
}

.underscore {
  text-decoration: underline;
}

</style>
