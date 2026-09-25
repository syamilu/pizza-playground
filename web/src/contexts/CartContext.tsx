'use client'

import React, { createContext, useContext, useEffect, useState } from 'react'
import { cartTotals, type CartItem } from '@/lib/checkout'

// v2: items keyed on API ids. The legacy `pp-cart` shape is ignored.
export const CART_STORAGE_KEY = 'pp_cart_v2'

interface CartContextType {
  items: CartItem[]
  addToCart: (item: CartItem) => void
  updateQuantity: (key: string, quantity: number) => void
  removeFromCart: (key: string) => void
  clearCart: () => void
  totals: ReturnType<typeof cartTotals>
}

const CartContext = createContext<CartContextType | undefined>(undefined)

// Storage is user-editable: keep only lines with the fields the cart and checkout depend on.
const isCartItem = (i: unknown): i is CartItem =>
  !!i &&
  typeof (i as CartItem).key === 'string' &&
  Number.isInteger((i as CartItem).pizzaId) &&
  Number.isInteger((i as CartItem).quantity) &&
  (i as CartItem).quantity > 0

function save(items: CartItem[]) {
  try {
    window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items))
  } catch (e) {
    console.warn('Failed to persist cart to localStorage:', e)
  }
}

export function CartProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<CartItem[]>([])
  const [hydrated, setHydrated] = useState(false)

  // `hydrated` stops the initial [] from clobbering the stored cart on first render.
  useEffect(() => {
    try {
      const raw = JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) ?? '[]')
      if (Array.isArray(raw)) setItems(raw.filter(isCartItem))
    } catch (e) {
      console.warn('Failed to restore cart from localStorage:', e)
    }
    setHydrated(true)
  }, [])

  useEffect(() => {
    if (hydrated) save(items)
  }, [items, hydrated])

  const addToCart = (item: CartItem) =>
    setItems((prev) =>
      prev.some((i) => i.key === item.key)
        ? prev.map((i) => (i.key === item.key ? { ...i, quantity: i.quantity + item.quantity } : i))
        : [...prev, item],
    )

  const updateQuantity = (key: string, quantity: number) =>
    setItems((prev) =>
      quantity <= 0 ? prev.filter((i) => i.key !== key) : prev.map((i) => (i.key === key ? { ...i, quantity } : i)),
    )

  const removeFromCart = (key: string) => setItems((prev) => prev.filter((i) => i.key !== key))

  // Writes storage synchronously: checkout clears the cart right before navigating to the gateway.
  const clearCart = () => {
    save([])
    setItems([])
  }

  return (
    <CartContext.Provider
      value={{ items, addToCart, updateQuantity, removeFromCart, clearCart, totals: cartTotals(items) }}
    >
      {children}
    </CartContext.Provider>
  )
}

export function useCart() {
  const context = useContext(CartContext)
  if (context === undefined) throw new Error('useCart must be used within a CartProvider')
  return context
}
