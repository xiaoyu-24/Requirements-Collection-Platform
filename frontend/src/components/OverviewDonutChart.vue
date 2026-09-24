<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

type DonutSegment = { key: string; label: string; count: number; color: string; to: RouteLocationRaw }
const props = defineProps<{
  label: string
  segments: DonutSegment[]
  total: number
  totalLabel: string
  totalTo: RouteLocationRaw
}>()

const radius = 70
const pointAt = (angle: number) => `${(95 + radius * Math.cos(angle)).toFixed(3)} ${(95 + radius * Math.sin(angle)).toFixed(3)}`
const arcs = computed(() => {
  if (props.total <= 0) return []
  let offset = -Math.PI / 2
  return props.segments.filter(segment => segment.count > 0).map(segment => {
    const angle = segment.count / props.total * Math.PI * 2
    const gap = Math.min(2 / radius, angle / 4)
    const start = offset + gap / 2
    const end = offset + angle - gap / 2
    offset += angle
    return {
      ...segment,
      path: `M ${pointAt(start)} A ${radius} ${radius} 0 ${end - start > Math.PI ? 1 : 0} 1 ${pointAt(end)}`,
      percent: Math.round(segment.count / props.total * 100),
    }
  })
})
</script>

<template>
  <div class="donut-layout">
    <div class="donut-chart">
      <svg viewBox="0 0 190 190" role="group" :aria-label="label">
        <circle cx="95" cy="95" r="70" class="donut-track" />
        <RouterLink v-for="segment in arcs" :key="segment.key" v-slot="{ href, navigate }" :to="segment.to" custom>
          <a :href="href" class="donut-segment-link" tabindex="0" :aria-label="`${segment.label}：${segment.count} 条，占 ${segment.percent}%，查看需求`" @click="navigate">
            <title>{{ segment.label }}：{{ segment.count }} 条 · {{ segment.percent }}%</title>
            <path :d="segment.path" class="donut-segment" :stroke="segment.color" />
          </a>
        </RouterLink>
      </svg>
      <RouterLink class="donut-center" :to="totalTo" :aria-label="`查看${totalLabel}，共 ${total} 条`" :title="`查看${totalLabel}`">
        <strong>{{ total }}</strong><span>{{ totalLabel }}</span>
      </RouterLink>
    </div>
    <div class="chart-legend">
      <RouterLink v-for="segment in arcs" :key="segment.key" :to="segment.to" class="legend-row" :title="`查看${segment.label}的 ${segment.count} 条需求`">
        <i :style="{ backgroundColor: segment.color }"></i><span>{{ segment.label }}</span><b>{{ segment.count }}</b><em>{{ segment.percent }}%</em>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.donut-layout { display: flex; align-items: center; gap: 28px; min-height: 244px; padding: 22px 24px 26px; }
.donut-chart { position: relative; width: 190px; flex: 0 0 190px; }
.donut-chart svg { display: block; width: 190px; height: 190px; }
.donut-track, .donut-segment { fill: none; stroke-width: 26; }
.donut-track { stroke: #f3f5f7; pointer-events: none; }
.donut-segment { stroke-linecap: butt; transition: stroke-width .15s, filter .15s; }
.donut-segment-link { cursor: pointer; outline: none; }
.donut-segment-link:hover .donut-segment { stroke-width: 30; filter: brightness(1.08); }
.donut-segment-link:focus-visible .donut-segment { stroke-width: 32; filter: brightness(.8); }
.donut-center { position: absolute; top: 50%; left: 50%; display: grid; width: 108px; height: 108px; place-content: center; border-radius: 50%; color: inherit; text-align: center; text-decoration: none; transform: translate(-50%, -50%); }
.donut-center:hover { background: #f0f7ff; }
.donut-center strong { color: rgba(0, 0, 0, .88); font-size: 26px; font-variant-numeric: tabular-nums; line-height: 1.1; }
.donut-center span { margin-top: 3px; color: rgba(0, 0, 0, .45); font-size: 12px; }
.donut-center:hover strong, .legend-row:hover span { color: #1677ff; }
.donut-center:focus-visible, .legend-row:focus-visible { outline: 2px solid #1677ff; outline-offset: 3px; }
.chart-legend { display: grid; min-width: 0; flex: 1; gap: 9px; }
.legend-row { display: grid; grid-template-columns: 9px minmax(0, 1fr) auto 42px; align-items: center; gap: 8px; width: 100%; padding: 0; border: 0; border-radius: 3px; background: transparent; color: inherit; text-align: left; text-decoration: none; }
.legend-row i { width: 9px; height: 9px; border-radius: 3px; }
.legend-row span { overflow: hidden; color: rgba(0, 0, 0, .65); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.legend-row b { color: rgba(0, 0, 0, .88); font-size: 13px; font-variant-numeric: tabular-nums; }
.legend-row em { color: rgba(0, 0, 0, .45); font-size: 12px; font-style: normal; text-align: right; }
@media (max-width: 720px) {
  .donut-layout { flex-direction: column; }
  .chart-legend { width: 100%; }
}
@media (prefers-reduced-motion: reduce) {
  .donut-segment { transition: none; }
}
</style>
