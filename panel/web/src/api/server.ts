import apiClient from './client';

export function startServer(id: number): Promise<void> {
  return apiClient.post(`/server/${id}/start-server`) as Promise<void>;
}

export function stopServer(id: number): Promise<void> {
  return apiClient.post(`/server/${id}/stop-server`) as Promise<void>;
}

export function restartServer(id: number): Promise<void> {
  return apiClient.post(`/server/${id}/restart-server`) as Promise<void>;
}

export function getStatus(id: number): Promise<any> {
  return apiClient.get(`/server/${id}/get-status`) as Promise<any>;
}

export function sendCommand(id: number, command: string): Promise<void> {
  return apiClient.post(`/server/${id}/send-command`, { command }) as Promise<void>;
}
