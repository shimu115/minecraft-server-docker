import axios from 'axios';
import { createDiscreteApi } from 'naive-ui';
import router from '@/router';
import { ErrorCode } from './types';
import type { ApiResponse } from './types';

const { message } = createDiscreteApi(['message']);

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

// 请求拦截器：自动附加 JWT
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：统一解包 + 错误处理
apiClient.interceptors.response.use(
  (response): any => {
    const body = response.data as ApiResponse;

    // 成功 → 提取 data 返回给调用方
    if (body.code === 200) {
      return body.data;
    }

    // 业务错误 → 统一提示
    handleBusinessError(body);
    return Promise.reject(body);
  },
  (error) => {
    // 网络级错误（超时、断网、5xx 等兜底）
    message.error('网络异常，请检查连接后重试');
    return Promise.reject(error);
  }
);

function handleBusinessError(body: ApiResponse) {
  switch (body.code) {
    case ErrorCode.UNAUTHORIZED:
      localStorage.removeItem('token');
      router.push('/login');
      message.warning('登录已过期，请重新登录');
      break;

    case ErrorCode.FORBIDDEN:
      message.error('无权限执行此操作');
      break;

    case ErrorCode.INSTANCE_NOT_BOUND:
      message.error('您未绑定该实例，无法操作');
      break;

    default:
      message.error(body.msg || '操作失败');
      break;
  }
}

export default apiClient;
