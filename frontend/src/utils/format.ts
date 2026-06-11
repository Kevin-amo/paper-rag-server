export function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '-';
}

export function textValue(value: unknown, fallback = '暂未识别') {
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : fallback;
  }
  if (value === null || value === undefined) {
    return fallback;
  }
  const text = String(value).trim();
  return text || fallback;
}

export function listValue(value: unknown) {
  return Array.isArray(value) ? value.map((item) => String(item)).filter(Boolean) : [];
}

export function formatJson(value: unknown) {
  if (!value) {
    return '{}';
  }
  return JSON.stringify(value, null, 2);
}
