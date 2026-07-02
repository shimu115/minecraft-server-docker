/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

/** 后端错误码 —— 前端只关注需要特殊处理的 code，其余走通用逻辑 */
export enum ErrorCode {
  SUCCESS = 200,

  // 通用
  BAD_REQUEST = 1000,
  INTERNAL_ERROR = 1001,

  // 认证
  UNAUTHORIZED = 2000,
  FORBIDDEN = 2001,
  INVALID_CREDENTIALS = 2002,

  // Key
  KEY_NOT_FOUND = 3000,
  KEY_ALREADY_EXISTS = 3001,
  KEY_ALREADY_BOUND = 3002,
  KEY_INVALID_FORMAT = 3003,
  KEY_REVOKED = 3004,
  KEY_BOUND_CANNOT_DELETE = 3005,

  // 实例
  INSTANCE_NOT_FOUND = 4000,
  INSTANCE_NAME_EXISTS = 4001,
  INSTANCE_NOT_BOUND = 4002,

  // 用户
  USER_NOT_FOUND = 5000,
  USERNAME_EXISTS = 5001,

  // Agent
  AGENT_UNREACHABLE = 6000,
  AGENT_TIMEOUT = 6001,
  AGENT_ERROR = 6002,
  AGENT_REFRESH_FAILED = 6003,
}

/** Key 列表项 */
export interface KeyItem {
  id: number;
  name: string;
  key_preview: string;
  status: 'active' | 'revoked';
  bound_instance: { id: number; name: string } | null;
  created_at: string;
  updated_at: string;
}

/** 实例列表项 */
export interface InstanceItem {
  id: number;
  name: string;
  host: string;
  port: number;
  server_type: string;
  mc_version: string;
  api_key: {
    id: number;
    name: string;
    key_preview: string;
    status: string;
  } | null;
  status: 'unknown' | 'running' | 'stopped' | 'error';
  created_at: string;
  updated_at: string;
}
