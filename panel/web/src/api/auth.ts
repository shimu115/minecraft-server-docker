import apiClient from './client';

export function login(username: string, password: string): Promise<{ token: string }> {
  return apiClient.post('/auth/login', { username, password }) as Promise<{ token: string }>;
}

export function getMe(): Promise<{ userId: number; username: string; role: string }> {
  return apiClient.get('/auth/get-me') as Promise<{ userId: number; username: string; role: string }>;
}
