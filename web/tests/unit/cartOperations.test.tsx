// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act, waitFor } from '@testing-library/react'
import type { CartItem } from '@/lib/checkout'
import { CartProvider, useCart, CART_STORAGE_KEY } from '@/contexts/CartContext'

function makeItem(key: string, unitPrice = 10, quantity = 1): CartItem {
  return {
    key, pizzaId: 1, pizzaName: `P-${key}`, sizeId: 1, sizeName: 'Medium', crustId: 1, crustName: 'Classic',
    toppingIds: [], toppingNames: [], unitPrice, quantity,
  }
}

function Probe() {
  const { items, addToCart, updateQuantity, removeFromCart, clearCart, totals } = useCart()
  ;(window as any).__cart = { addToCart, updateQuantity, removeFromCart, clearCart }
  return (
    <div>
      <div data-testid="count">{items.length}</div>
      <div data-testid="total-items">{totals.count}</div>
      <div data-testid="subtotal">{totals.subtotal}</div>
      <div data-testid="total">{totals.total}</div>
      <ul>
        {items.map((item, i) => (
          <li key={item.key} data-testid={`item-${i}`}>{`${item.key}x${item.quantity}`}</li>
        ))}
      </ul>
    </div>
  )
}

async function mountCart() {
  render(<CartProvider><Probe /></CartProvider>)
  await waitFor(() => expect(screen.getByTestId('count')).toBeTruthy())
}

const cart = () => (window as any).__cart as {
  addToCart: (i: CartItem) => void
  updateQuantity: (key: string, qty: number) => void
  removeFromCart: (key: string) => void
  clearCart: () => void
}

describe('CartContext operations', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  it('addToCart appends an item', async () => {
    await mountCart()
    await act(async () => cart().addToCart(makeItem('a')))
    expect(screen.getByTestId('count').textContent).toBe('1')
    expect(screen.getByTestId('item-0').textContent).toBe('ax1')
  })

  it('adding the same configuration (same key) merges quantity; a different key is a new line', async () => {
    await mountCart()
    await act(async () => cart().addToCart(makeItem('a', 10, 1)))
    await act(async () => cart().addToCart(makeItem('a', 10, 2)))
    await act(async () => cart().addToCart(makeItem('b')))
    expect(screen.getByTestId('count').textContent).toBe('2')
    expect(screen.getByTestId('item-0').textContent).toBe('ax3')
    expect(screen.getByTestId('item-1').textContent).toBe('bx1')
  })

  it('updateQuantity sets the quantity, and 0 removes the line', async () => {
    await mountCart()
    await act(async () => cart().addToCart(makeItem('a')))
    await act(async () => cart().updateQuantity('a', 4))
    expect(screen.getByTestId('item-0').textContent).toBe('ax4')
    await act(async () => cart().updateQuantity('a', 0))
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  it('removeFromCart removes by key and recalculates totals', async () => {
    await mountCart()
    await act(async () => cart().addToCart(makeItem('a', 10)))
    await act(async () => cart().addToCart(makeItem('b', 25)))
    expect(screen.getByTestId('subtotal').textContent).toBe('35')
    await act(async () => cart().removeFromCart('a'))
    expect(screen.getByTestId('count').textContent).toBe('1')
    expect(screen.getByTestId('item-0').textContent).toBe('bx1')
    expect(screen.getByTestId('subtotal').textContent).toBe('25')
  })

  it('clearCart empties the cart and storage immediately', async () => {
    await mountCart()
    await act(async () => cart().addToCart(makeItem('a')))
    await act(async () => cart().clearCart())
    expect(screen.getByTestId('count').textContent).toBe('0')
    expect(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) ?? '[]')).toHaveLength(0)
  })

  it('totals: count sums quantities, total adds the flat 1.00 fee', async () => {
    await mountCart()
    await act(async () => cart().addToCart(makeItem('a', 10, 2)))
    await act(async () => cart().addToCart(makeItem('b', 13.5, 3)))
    expect(screen.getByTestId('total-items').textContent).toBe('5')
    expect(screen.getByTestId('subtotal').textContent).toBe('60.5')
    expect(screen.getByTestId('total').textContent).toBe('61.5')
  })

  it('persists under pp_cart_v2 and ignores the legacy pp-cart key', async () => {
    window.localStorage.setItem('pp-cart', JSON.stringify([{ pizza: { id: 'x' }, totalPrice: 9, quantity: 1 }]))
    await mountCart()
    expect(screen.getByTestId('count').textContent).toBe('0')
    await act(async () => cart().addToCart(makeItem('a')))
    await waitFor(() => {
      expect(CART_STORAGE_KEY).toBe('pp_cart_v2')
      expect(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) as string)).toHaveLength(1)
    })
  })

  it('hydrates a stored cart', async () => {
    window.localStorage.setItem('pp_cart_v2', JSON.stringify([makeItem('a', 10, 2)]))
    await mountCart()
    await waitFor(() => expect(screen.getByTestId('item-0').textContent).toBe('ax2'))
  })

  it('drops stored lines missing key, pizzaId or quantity', async () => {
    const bad = [{ pizzaId: 1, quantity: 1 }, { ...makeItem('b', 5, 1), pizzaId: '1' }, { ...makeItem('c', 5, 1), quantity: undefined }]
    window.localStorage.setItem('pp_cart_v2', JSON.stringify([...bad, makeItem('a', 10, 2)]))
    await mountCart()
    await waitFor(() => expect(screen.getByTestId('item-0').textContent).toBe('ax2'))
    expect(screen.queryByTestId('item-1')).toBeNull()
  })
})
