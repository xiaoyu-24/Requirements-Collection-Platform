<script setup lang="ts">
defineProps<{ active: boolean }>()
</script>

<template>
  <div class="page-refresh-bar" :class="{ active }" aria-hidden="true">
    <span></span>
  </div>
</template>

<style scoped>
.page-refresh-bar {
  position: sticky;
  top: 0;
  z-index: 7;
  height: 2px;
  overflow: hidden;
  pointer-events: none;
}

.page-refresh-bar span {
  display: block;
  width: 0;
  height: 100%;
  border-radius: 0 2px 2px 0;
  background: linear-gradient(90deg, #1677ff, #69b1ff);
  opacity: 0;
  transition: opacity .2s ease;
}

.page-refresh-bar.active span {
  opacity: 1;
  animation: progress-slide 1.1s ease-in-out infinite;
}

@keyframes progress-slide {
  0% { width: 0; margin-left: 0; }
  55% { width: 62%; margin-left: 18%; }
  100% { width: 0; margin-left: 100%; }
}
</style>
