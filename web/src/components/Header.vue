<script setup>

import UserAvatar from "./UserAvatar.vue";
import {useUserIdStore} from "../stores/userId.js";
import {getXsrfToken} from "../utils.js";

const userIdStore = useUserIdStore()

async function logout() {
  await fetch("/api/logout", {
    method: "POST",
    headers: {
      "X-XSRF-TOKEN": getXsrfToken(),
    },
  })
  userIdStore.clearUserId()
  window.location.href = "/"
}
</script>

<template>
  <div class="forum-logo-outer">
    <span class="flex-row gap-small" v-if="userIdStore.isLoggedIn">
      <a @click="logout" class="logout-link">登出</a>
    </span>
    <span v-else style="width: 52px"></span>
    <a href="https://station.smbx.world"><img src="/static/smbx-world.png" alt="SMBX World Logo" class="forum-logo" /></a>
    <UserAvatar />
  </div>
</template>

<style scoped>
.forum-logo-outer {
  width: 100%;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2em;
}

.forum-logo {
  height: 64px;
  filter: drop-shadow(2px 2px 4px #000000aa);
  transition: filter, translate 200ms ease-in-out;
}

.forum-logo:hover {
  translate: -2px -2px;
  filter: drop-shadow(4px 4px 4px #000000aa);
}

.logout-link {
  cursor: pointer;
  font-size: 14px;
}

@media (max-width: 600px) {
  .forum-logo {
    height: 40px;
    position: relative;
    right: 1rem; /* 移动设备上给头像留出空间 */
  }
}

</style>
