<template>
  <n-modal v-model:show="showModal" preset="dialog" :title="title"
    :positive-text="confirmText" :negative-text="cancelText"
    :type="danger ? 'warning' : 'default'"
    @positive-click="$emit('confirm')"
    @negative-click="$emit('cancel')">
    <p>{{ content }}</p>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { NModal } from 'naive-ui';

const props = defineProps<{
  visible: boolean;
  title: string;
  content: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
}>();

defineEmits<{
  confirm: [];
  cancel: [];
}>();

const showModal = ref(props.visible);
watch(() => props.visible, (v) => { showModal.value = v; });
watch(showModal, (v) => { if (!v) { /* closed */ } });
</script>
