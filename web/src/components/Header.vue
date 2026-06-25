<script setup>

import UserAvatar from "./UserAvatar.vue";
</script>

<template>
  <div class="forum-logo-outer">
    <span style="width: 52px"></span>
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

@media (max-width: 600px) {
  .forum-logo {
    height: 40px;
    position: relative;
    right: 1rem; /* 移动设备上给头像留出空间 */
  }
}

</style>
