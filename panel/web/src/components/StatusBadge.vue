<template>
  <n-tag :type="tagType" size="small" :bordered="false">
    {{ label }}
  </n-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NTag } from 'naive-ui';

const props = defineProps<{
  status: string;
}>();

const tagType = computed(() => {
  switch (props.status) {
    case 'running': return 'success';
    case 'stopped': return 'error';
    case 'error': return 'warning';
    case 'active': return 'success';
    case 'revoked': return 'error';
    default: return 'default';
  }
});

const label = computed(() => {
  switch (props.status) {
    case 'running': return '运行中';
    case 'stopped': return '已停止';
    case 'error': return '异常';
    case 'unknown': return '未知';
    case 'active': return '活跃';
    case 'revoked': return '已吊销';
    default: return props.status;
  }
});
</script>
