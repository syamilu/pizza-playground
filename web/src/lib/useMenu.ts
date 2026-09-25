'use client'

import { useEffect, useState } from 'react'
import { getMenu } from '@/lib/api'
import type { Menu } from '@/types/api'

// One getMenu() per page load, shared by home, menu and the customizer. A failed fetch is not cached.
let cached: Promise<Menu> | null = null

export function loadMenu(): Promise<Menu> {
  cached ??= getMenu().catch((e) => {
    cached = null
    throw e
  })
  return cached
}

export function useMenu() {
  const [menu, setMenu] = useState<Menu | null>(null)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    let cancelled = false
    loadMenu().then(
      (m) => !cancelled && setMenu(m),
      (e) => !cancelled && setError(e),
    )
    return () => {
      cancelled = true
    }
  }, [])

  return { menu, error, loading: !menu && !error }
}
