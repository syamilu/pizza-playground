// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'

vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return { ...actual, me: vi.fn() }
})

import * as api from '@/lib/api'
import { AuthProvider, useAuth } from '@/contexts/AuthContext'

function Probe() {
  const { token, authReady } = useAuth()
  return <div data-testid="state">{authReady ? `ready:${token ?? 'none'}` : 'loading'}</div>
}

const mount = () => render(<AuthProvider><Probe /></AuthProvider>)

describe('AuthContext hydration', () => {
  beforeEach(() => {
    window.localStorage.clear()
    window.localStorage.setItem(api.TOKEN_KEY, 't1')
  })

  it('logs out when /auth/me rejects the token (401)', async () => {
    vi.mocked(api.me).mockRejectedValueOnce(new api.ApiError(401, 'unauthorized'))
    mount()
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('ready:none'))
    expect(window.localStorage.getItem(api.TOKEN_KEY)).toBeNull()
  })

  it('keeps the token on a network error', async () => {
    vi.mocked(api.me).mockRejectedValueOnce(new TypeError('Failed to fetch'))
    mount()
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('ready:t1'))
    expect(window.localStorage.getItem(api.TOKEN_KEY)).toBe('t1')
  })

  it('keeps the token on a 500', async () => {
    vi.mocked(api.me).mockRejectedValueOnce(new api.ApiError(500, 'boom'))
    mount()
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('ready:t1'))
  })
})
