import apiClient from './client';
import type {KeyItem} from './types';

export function registerKey(name: string, keyValue: string): Promise<KeyItem> {
    return apiClient.post('/admin/keys/register', {name, keyValue}) as Promise<KeyItem>;
}

export function listKeys(): Promise<KeyItem[]> {
    return apiClient.get('/admin/keys/list') as Promise<KeyItem[]>;
}

export function getKey(id: number): Promise<KeyItem> {
    return apiClient.get(`/admin/keys/${id}/get`) as Promise<KeyItem>;
}

export function deleteKey(id: number): Promise<void> {
    return apiClient.delete(`/admin/keys/${id}/delete`) as Promise<void>;
}

export function revokeKey(id: number): Promise<{ id: number; status: string }> {
    return apiClient.post(`/admin/keys/${id}/revoke`) as Promise<{ id: number; status: string }>;
}
