import apiClient from './client';
import type { InstanceItem } from './types';

export interface CreateInstanceParams {
  name: string;
  apiKeyId: number;
  host: string;
  port: number;
  serverType: string;
  mcVersion: string;
}

export interface UpdateInstanceParams {
  name?: string;
  host?: string;
  port?: number;
  serverType?: string;
  mcVersion?: string;
}

export function createInstance(params: CreateInstanceParams): Promise<InstanceItem> {
  return apiClient.post('/admin/instances/create', {
    name: params.name,
    api_key_id: params.apiKeyId,
    host: params.host,
    port: params.port,
    server_type: params.serverType,
    mc_version: params.mcVersion,
  }) as Promise<InstanceItem>;
}

export function listInstances(): Promise<InstanceItem[]> {
  return apiClient.get('/admin/instances/list') as Promise<InstanceItem[]>;
}

export function getInstance(id: number): Promise<InstanceItem> {
  return apiClient.get(`/admin/instances/${id}/get`) as Promise<InstanceItem>;
}

export function updateInstance(id: number, params: UpdateInstanceParams): Promise<InstanceItem> {
  return apiClient.put(`/admin/instances/${id}/update`, params) as Promise<InstanceItem>;
}

export function deleteInstance(id: number): Promise<void> {
  return apiClient.delete(`/admin/instances/${id}/delete`) as Promise<void>;
}

export function bindKey(id: number, apiKeyId: number): Promise<{ id: number; api_key_id: number }> {
  return apiClient.put(`/admin/instances/${id}/bind-key`, { api_key_id: apiKeyId }) as Promise<{ id: number; api_key_id: number }>;
}

export function refreshKey(id: number): Promise<{
  instance_id: number;
  previous_key: { id: number; key_preview: string; status: string };
  new_key: { id: number; key_preview: string; status: string };
}> {
  return apiClient.put(`/admin/instances/${id}/refresh-key`) as Promise<any>;
}

export function healthCheck(id: number): Promise<{
  instance_id: number;
  instance_name: string;
  go_api_health: string;
}> {
  return apiClient.get(`/admin/instances/${id}/health-check`) as Promise<any>;
}
