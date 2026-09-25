import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { api, ApiError } from '@/lib/api'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('api()', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    localStorage.clear()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('prefixes /api/v1 on the API base URL', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { pizzas: [] }))
    const body = await api('/menu')
    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/menu')
    expect(body).toEqual({ pizzas: [] })
  })

  it('attaches a bearer token when one is stored', async () => {
    localStorage.setItem('pp_token', 'abc')
    fetchMock.mockResolvedValue(jsonResponse(200, {}))
    await api('/orders/mine')
    const headers = new Headers(fetchMock.mock.calls[0][1].headers)
    expect(headers.get('Authorization')).toBe('Bearer abc')
  })

  it('omits the token when auth is false', async () => {
    localStorage.setItem('pp_token', 'abc')
    fetchMock.mockResolvedValue(jsonResponse(200, {}))
    await api('/auth/login', { method: 'POST', auth: false })
    const headers = new Headers(fetchMock.mock.calls[0][1].headers)
    expect(headers.get('Authorization')).toBeNull()
  })

  it('throws ApiError with status, message and requestId on non-2xx', async () => {
    fetchMock.mockResolvedValue(jsonResponse(404, { error: 'order not found', requestId: 'r1' }))
    const err = await api('/orders/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(404)
    expect(err.message).toBe('order not found')
    expect(err.requestId).toBe('r1')
  })

  it('returns undefined for 204', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    expect(await api('/x')).toBeUndefined()
  })
})
