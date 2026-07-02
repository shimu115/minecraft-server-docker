/**
 * Key 脱敏显示：前4位 + ****...**** + 后4位
 */
export function keyPreview(key: string): string {
  if (!key || key.length < 9) return '****';
  return key.substring(0, 4) + '****...****' + key.substring(key.length - 4);
}

/**
 * 日期格式化
 */
export function formatDate(dateStr: string): string {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
