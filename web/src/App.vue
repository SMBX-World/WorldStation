<script setup>
import {onMounted} from "vue";
import ScrollingBackground from "./components/ScrollingBackground.vue";
import Header from "./components/Header.vue";
import Spring from "./components/Spring.vue";
import Footer from "./components/Footer.vue";
import RouterView from "./views/RouterView.vue";
import LoginModal from "./components/LoginModal.vue";
import {useUserIdStore} from "./stores/userId.js";

const userIdStore = useUserIdStore()

onMounted(() => {
  userIdStore.fetchUser()
})
</script>


<template>
  <ScrollingBackground />
  <Header />
  <RouterView />
  <Footer />
  <Spring />
  <LoginModal v-if="userIdStore.loginOpen" @close="userIdStore.closeLogin()" />
</template>

<style scoped>
</style>
