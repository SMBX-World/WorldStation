<script setup>
const currentYear = new Date().getFullYear()
</script>

<template>
  <div class="footer">
    <p><span>© 2022-{{ currentYear }}</span>&nbsp;<span><a href="https://smbx.world">SMBX World 中文社区</a>&nbsp;&&nbsp;</span><span><a href="https://chyk.ink">斬風千雪 ❄️</a></span></p>
  </div>
</template>

<style scoped>
* {
  color: #1a1a1a;
}

@media (prefers-color-scheme: dark) {
  * {
    color: #f0f0f0;
  }
}

span {
  display: inline-block;
}
</style>
