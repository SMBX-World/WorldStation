<script setup>
// 无限滚动的世界地图列表
// credits: https://learnvue.co/articles/vue-infinite-scrolling

import {onMounted, onUnmounted, ref, watch} from 'vue'
import WorldMapItem from "./WorldMapItem.vue";

// 页面大小
const PAGE_SIZE = 20

// 变量
let currentPage = 0
let scrolledToEnd = false
let requestPending = false

// props
const filters = defineProps({
  title: {
    type: String,
    default: ""
  },
  version: {
    type: String,
    default: ""
  },
  userId: {
    type: Number,
    default: -1
  },
  sort: {
    type: String,
    default: ""
  }
})

const emit = defineEmits(['edit'])

function onEdit(mapId) {
  emit('edit', mapId)
}

// refs
const worldMaps = ref([])
const scrollComponent = ref(null)

const getWorldMaps = async (page = 0, pageSize = PAGE_SIZE, filters = buildFilters()) => {
  const result = await fetch(`/api/worldmaps?page=${page}&pageSize=${pageSize}${filters}`)
  if (!result.ok) {
    throw new Error(`获取世界地图失败: ${result.statusText}`)
  }
  const data = await result.json()
  const worldmaps = data.data || []
  if (worldmaps.length < pageSize) {
    // 如果返回的世界地图数量少于 pageSize，说明已经没有更多数据了
    scrolledToEnd = true
  }

  return worldmaps
}


onMounted(() => {
  // 监听滚动事件
  window.addEventListener("scroll", handleScroll)
  // 一开始先整点地图
  currentPage = 0
  loadMoreWorldMaps()
})

onUnmounted(() => {
  window.removeEventListener("scroll", handleScroll)
})

const loadMoreWorldMaps = async () => {
  if (scrolledToEnd || !scrollComponent.value) return

  requestPending = true
  const newWorldMaps = await getWorldMaps(currentPage)
  worldMaps.value.push(...newWorldMaps)
  currentPage += 1
  requestPending = false
}

const handleScroll = (event) => {
  if (requestPending || scrolledToEnd || !scrollComponent.value) return

  const element = scrollComponent.value
  if (element.getBoundingClientRect().bottom < window.innerHeight) {
    loadMoreWorldMaps()
  }
}


// 2025.8.17: worldMaps 随着 filters 更改逻辑
watch(() => filters.title, async () => await updateFilters())
watch(() => filters.version, async () => await updateFilters())
watch(() => filters.userId, async () => await updateFilters())
watch(() => filters.sort, async () => await updateFilters())

const cacheKeys = []  // ["title+version+userId+sort"]，记录缓存顺序
const worldMapCache = {}  // {"title+version+userId+sort": worldMaps}，缓存实际内容

function deleteOldCache(limit = 10) {
  // 自动删除最旧的缓存
  if (cacheKeys.length > limit) {
    const keyToDelete = cacheKeys.shift()
    delete worldMapCache[keyToDelete]
  }
}

function buildFilters(title = filters.title, version = filters.version, userId = filters.userId, sort = filters.sort) {
  let filters = ""
  if (title) {
    filters += `&query=${encodeURIComponent(title)}`
  }
  if (version) {
    filters += `&version=${encodeURIComponent(version)}`
  }
  if (userId && userId !== -1) {
    filters += `&uploader=${userId}`
  }
  if (sort) {
    filters += `&sort=${encodeURIComponent(sort)}`
  }
  return filters
}

async function updateFilters() {
  const newTitle = filters.title
  const newVersion = filters.version
  const newUserId = filters.userId
  const newSort = filters.sort
  console.log(`更新地图过滤器: title="${newTitle}", version="${newVersion}", userId=${newUserId}, sort="${newSort}"`)

  // 确定旧 key，把当前数据存入缓存
  const currentKey = cacheKeys.length > 0 ? cacheKeys[cacheKeys.length - 1] : null
  if (currentKey && !worldMapCache[currentKey]) {
    worldMapCache[currentKey] = worldMaps.value.slice()
    cacheKeys.push(currentKey)
    deleteOldCache()
  }

  const newKey = `${newTitle}+${newVersion}+${newUserId}+${newSort}`

  // (此时处理 UI
  worldMaps.value = []
  scrolledToEnd = false
  requestPending = false
  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  })

  // 之后，如果缓存中有新标题和版本的 worldMaps，就直接使用缓存
  if (worldMapCache[newKey]) {
    console.log(`使用缓存的地图: ${newKey}`)
    worldMaps.value = worldMapCache[newKey]
    // 更新缓存的顺序
    cacheKeys.splice(cacheKeys.indexOf(newKey), 1)
    cacheKeys.push(newKey)
  } else {
    // 否则就重新获取世界地图
    const filterStr = buildFilters(newTitle, newVersion, newUserId, newSort)
    const result = await getWorldMaps(0, PAGE_SIZE, filterStr)
    worldMaps.value = result
    worldMapCache[newKey] = result
    cacheKeys.push(newKey)
    deleteOldCache()
  }
}

defineExpose({
  refreshMapItem(updatedMap) {
    const index = worldMaps.value.findIndex(m => m.id === updatedMap.id)
    if (index !== -1) {
      worldMaps.value[index] = updatedMap
    }
  },
  removeMapItem(mapId) {
    worldMaps.value = worldMaps.value.filter(m => m.id !== mapId)
  }
})

</script>

<template>
  <div ref="scrollComponent" class="worldmap-list">
    <WorldMapItem v-for="wm in worldMaps" :world-map="wm" :key="wm.id" @edit="onEdit"/>
    <!-- 为了防止某些人的显示器超级无敌大 -->
    <div class="load-more">
      <span @click="loadMoreWorldMaps" v-if="!scrolledToEnd">
        加载更多地图
      </span>
    </div>
  </div>
</template>

<style scoped>
.worldmap-list {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.load-more {
  margin: 1em;
  cursor: pointer;
}
</style>
