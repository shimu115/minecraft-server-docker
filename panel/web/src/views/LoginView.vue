<template>
  <div style="display: flex; justify-content: center; align-items: center; height: 100vh; background: #f0f2f5">
    <n-card title="MC Panel 登录" style="width: 400px">
      <n-form ref="formRef" :model="form" :rules="rules">
        <n-form-item path="username" label="用户名">
          <n-input v-model:value="form.username" placeholder="请输入用户名" @keyup.enter="handleLogin" />
        </n-form-item>
        <n-form-item path="password" label="密码">
          <n-input v-model:value="form.password" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="handleLogin">登录</n-button>
      </n-form>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NCard, NForm, NFormItem, NInput, NButton, createDiscreteApi } from 'naive-ui';
import { login } from '@/api/auth';

const { message } = createDiscreteApi(['message']);
const router = useRouter();

const form = ref({ username: 'root', password: 'root123' });
const loading = ref(false);

const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }],
};

async function handleLogin() {
  loading.value = true;
  try {
    const result = await login(form.value.username, form.value.password);
    localStorage.setItem('token', result.token);
    message.success('登录成功');
    router.push('/');
  } catch {
    loading.value = false;
  }
}
</script>
