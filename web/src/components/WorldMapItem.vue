<script setup>
import {GAME_VERSION_INFO, DOWNLOAD_PROVIDER_INFO} from "../utils.js"
import {onMounted, ref} from "vue";
import {useUserIdStore} from "../stores/userId.js";

const {worldMap} = defineProps({
  worldMap: {
    type: Object,
    required: true
  }
});

const emit = defineEmits(['edit'])

const userIdStore = useUserIdStore();

const ver = ref(GAME_VERSION_INFO[worldMap.gameVersion])
const down = ref(DOWNLOAD_PROVIDER_INFO[worldMap.downloadProvider])
const showDown = ref(false)
const itemRef = ref(null)

onMounted(() => {
  itemRef.value?.addEventListener('mouseenter', () => showDown.value = true)
  itemRef.value?.addEventListener('mouseleave', () => showDown.value = false)
})

</script>

<template>
  <div class="flex-row world-map-item" ref="itemRef">
    <img
        :src="ver.icon"
        :alt="ver.name"
        class="version-icon no-drag" />
    <div class="flex-column world-map-details">
      <span class="ellipsis">
        <a class="ellipsis" :href="worldMap.downloadUrl"><strong>{{worldMap.title}}</strong></a>
        <span v-if="worldMap.author"> by {{worldMap.author}}</span>
      </span>
      <span class="ellipsis">游戏版本: {{ver.name}}</span>
    </div>
    <a class="flex-right-row"
       v-if="(userIdStore.userId === worldMap.uploader || userIdStore.isAdmin) && showDown"
       @click="emit('edit', worldMap.id)"
    >
      <img
          src="/static/qblock.gif"
          alt="编辑信息"
          class="download-icon no-drag" />
      <span class="download-text">
        编辑信息
      </span>
    </a>
    <a class="flex-right-row"
       :href="worldMap.downloadUrl"
       target="_blank"
       rel="noopener noreferrer"
       v-if="showDown"
    >
      <img
          :src="down.icon"
          alt="下载"
          class="download-icon no-drag" />
      <span class="download-text">
        {{ down.name }}
      </span>
    </a>
  </div>
</template>

<style scoped>
.world-map-item {
  justify-content: flex-start;
  gap: 1em;
  padding: 0.5em;
  width: 100%;
  border-radius: 8px;
  transition: background-color 100ms ease-in-out;
  max-width: 100%;
}
.world-map-item:hover {
  background-color: #f0f0f05a;
}
.world-map-details {
  min-width: 0;
  max-width: 100%;
  flex: 1;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.flex-column {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.flex-right-row {
  margin-left: auto;
  margin-right: 0.5em;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 0.5em;
}
.version-icon {
  width: 48px;
}
.download-icon {
  width: 32px;
}
@media (max-width: 600px) {
  .version-icon {
    width: 32px;  /* 移动设备上缩小版本图标 */
  }
  .download-icon {
    display: none;  /* 移动设备上隐藏下载图标 */
  }
}
.download-text {
  white-space: nowrap; /* 中文换个蛋的行 */
}
@media (prefers-color-scheme: light) {
  * {
    color: #fff;
    text-shadow: 1px 1px 1px #000, -1px -1px 1px #000, 1px -1px 1px #000, -1px 1px 1px #000;
  }
}
</style>
