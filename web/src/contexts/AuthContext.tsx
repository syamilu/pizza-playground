'use client'

import React, { createContext, useCallback, useContext, useEffect, useState } from 'react'
import * as apiClient from '@/lib/api'
import { TOKEN_KEY } from '@/lib/api'
import type { AuthResponse, RegisterRequest, User } from '@/types/api'

interface AuthContextType {
  user: User | null
  token: string | null
  /** False until the stored token has been checked against /auth/me. */
  authReady: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<User>
  register: (req: RegisterRequest) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [authReady, setAuthReady] = useState(false)

  const logout = useCallback(() => {
    try { localStorage.removeItem(TOKEN_KEY) } catch { /* storage unavailable */ }
    setToken(null)
    setUser(null)
  }, [])

  useEffect(() => {
    let stored: string | null = null
    try { stored = localStorage.getItem(TOKEN_KEY) } catch { /* storage unavailable */ }
    if (!stored) {
      setAuthReady(true)
      return
    }
    setToken(stored)
    let cancelled = false
    apiClient
      .me()
      .then((u) => !cancelled && setUser(u))
      .catch((e) => {
        // Only a rejected token logs out; a network error or 5xx keeps it for the next load.
        if (!cancelled && e instanceof apiClient.ApiError && (e.status === 401 || e.status === 403)) logout()
      })
      .finally(() => !cancelled && setAuthReady(true))
    return () => {
      cancelled = true
    }
  }, [logout])

  const accept = ({ token, user }: AuthResponse) => {
    try { localStorage.setItem(TOKEN_KEY, token) } catch { /* storage unavailable */ }
    setToken(token)
    setUser(user)
    return user
  }

  const login = async (email: string, password: string) => accept(await apiClient.login(email, password))
  const register = async (req: RegisterRequest) => accept(await apiClient.register(req))

  const isAdmin = user?.role === 'OWNER' || user?.role === 'CASHIER'

  return (
    <AuthContext.Provider value={{ user, token, authReady, isAdmin, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) throw new Error('useAuth must be used within an AuthProvider')
  return context
}

/** Thin compatibility shim for admin pages written against the old AdminContext. */
export function useAdmin() {
  const { user, isAdmin, logout } = useAuth()
  return { user, isAdmin, logout }
}
