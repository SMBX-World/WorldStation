<script setup>
import {ref} from "vue";
import Motd from "../components/Motd.vue";
import FilterBar from "../components/FilterBar.vue";
import WorldMapList from "../components/WorldMapList.vue";
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

</script>

<template>
  <Motd />
  <FilterBar :isLoggedIn="userIdStore.isLoggedIn" @applyFilter="applyFilter" />
  <WorldMapList :title="titleFilter" :version="versionFilter" :userId="userIdFilter" :sort="sortFilter" />
</template>

<style scoped>

</style>
