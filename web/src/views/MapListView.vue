<script setup>
import {ref} from "vue";
import Motd from "../components/Motd.vue";
import FilterBar from "../components/FilterBar.vue";
import WorldMapList from "../components/WorldMapList.vue";
import EditMapModal from "../components/EditMapModal.vue";
import {useUserIdStore} from "../stores/userId.js";

const titleFilter = ref("")
const versionFilter = ref("")
const userIdFilter = ref(-1)
const sortFilter = ref("")

function applyFilter(title, version, userId, sort) {
  titleFilter.value = title
  versionFilter.value = version
  userIdFilter.value = userId
  sortFilter.value = sort || ""
}

const userIdStore = useUserIdStore()

// 模态框状态
const editingMapId = ref(null)
const worldMapListRef = ref(null)

function onEdit(mapId) {
  editingMapId.value = mapId
}

function onCloseModal() {
  editingMapId.value = null
}

function onMapUpdated(updatedMap) {
  worldMapListRef.value?.refreshMapItem(updatedMap)
}

function onMapDeleted(mapId) {
  worldMapListRef.value?.removeMapItem(mapId)
}

</script>

<template>
  <Motd />
  <FilterBar :isLoggedIn="userIdStore.isLoggedIn" @applyFilter="applyFilter" />
  <WorldMapList ref="worldMapListRef" :title="titleFilter" :version="versionFilter" :userId="userIdFilter" :sort="sortFilter" @edit="onEdit" />
  <EditMapModal v-if="editingMapId" :mapId="editingMapId" @close="onCloseModal" @updated="onMapUpdated" @deleted="onMapDeleted" />
</template>

<style scoped>

</style>
