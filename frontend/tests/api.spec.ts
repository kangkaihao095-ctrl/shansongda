import { describe, expect, it, vi } from 'vitest'
import { api, download, setToken, token, upload } from '../src/api.js'

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: { 'Content-Type': 'application/json', ...(init.headers as Record<string, string> | undefined) }
  })
}

describe('api', () => {
  it('attaches the bearer token and returns JSON', async () => {
    setToken('abc')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true, data: { role: 'USER' } }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(api('/api/me')).resolves.toEqual({ ok: true, data: { role: 'USER' } })
    expect(token()).toBe('abc')
    expect(fetchMock).toHaveBeenCalledWith('/api/me', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer abc' })
    }))
  })

  it('serializes JSON bodies', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    await api('/api/orders', { method: 'POST', body: { merchantId: 3 } })
    const options = fetchMock.mock.calls[0][1] as RequestInit
    expect(options.body).toBe(JSON.stringify({ merchantId: 3 }))
    expect((options.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })

  it('throws the server error message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ok: false, message: '库存不足' }, { status: 409 })))
    await expect(api('/api/activities/1/seckill')).rejects.toThrow('库存不足')
  })

  it('upload sends FormData without json content-type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true, data: { avatarUrl: '/a.png' } }))
    vi.stubGlobal('fetch', fetchMock)
    await upload('/api/me/avatar', new File(['x'], 'a.png'))
    const options = fetchMock.mock.calls[0][1] as RequestInit
    expect(options.body).toBeInstanceOf(FormData)
    expect((options.headers as Record<string, string>)['Content-Type']).toBeUndefined()
  })

  it('download fetches csv with bearer token', async () => {
    setToken('abc')
    const click = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({ click, href: '', download: '' } as unknown as HTMLElement)
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:csv', revokeObjectURL: vi.fn() })
    const fetchMock = vi.fn().mockResolvedValue(new Response('日期,单量\n', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await download('/api/merchant/report.csv?range=7d', 'r.csv')
    expect(fetchMock).toHaveBeenCalledWith('/api/merchant/report.csv?range=7d', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer abc' })
    }))
    expect(click).toHaveBeenCalled()
  })
})
