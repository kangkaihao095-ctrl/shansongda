const KEY = 'ssd.token'

export function token() {
  return localStorage.getItem(KEY)
}

export function setToken(value) {
  if (value) localStorage.setItem(KEY, value)
  else localStorage.removeItem(KEY)
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  const t = token()
  if (t) headers.Authorization = `Bearer ${t}`
  const res = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok || json.ok === false) {
    const err = new Error(json.message || res.statusText || '请求失败')
    err.status = res.status
    err.payload = json
    throw err
  }
  return json
}

export async function upload(path, file) {
  const headers = {}
  const t = token()
  if (t) headers.Authorization = `Bearer ${t}`
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(path, { method: 'POST', headers, body: form })
  const json = await res.json().catch(() => ({}))
  return json
}

export async function download(path, filename) {
  const headers = {}
  const t = token()
  if (t) headers.Authorization = `Bearer ${t}`
  const res = await fetch(path, { headers })
  if (!res.ok) {
    const json = await res.json().catch(() => ({}))
    throw new Error(json.message || res.statusText || '下载失败')
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || 'report.csv'
  a.click()
  URL.revokeObjectURL(url)
}
