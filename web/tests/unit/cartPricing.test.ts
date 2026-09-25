import { describe, it, expect } from 'vitest'
import { buildCartItem, cartTotals, SERVICE_FEE } from '@/lib/checkout'
import type { Menu } from '@/types/api'

// Client-side price preview. Same formula as the API's PricingService:
// unit = base + sizeDelta + crustDelta + sum(toppingDelta); line = unit x qty; total = subtotal + flat 1.00.

const menu: Menu = {
  pizzas: [{ id: 1, name: 'Margherita', description: '', basePrice: 20, category: 'veg', imageUrl: null }],
  sizes: [
    { id: 1, name: 'Small', priceDelta: -3 },
    { id: 2, name: 'Medium', priceDelta: 0 },
    { id: 3, name: 'Large', priceDelta: 5.5 },
  ],
  crusts: [
    { id: 1, name: 'Classic', priceDelta: 0 },
    { id: 2, name: 'Thick', priceDelta: 2 },
  ],
  toppings: [
    { id: 1, name: 'Cheese', priceDelta: 2 },
    { id: 2, name: 'Mushroom', priceDelta: 1.5 },
  ],
}
const pizza = menu.pizzas[0]

describe('buildCartItem', () => {
  it('unit price is base + size + crust + topping deltas from the menu', () => {
    const item = buildCartItem(menu, pizza, 3, 2, [1, 2], 1)
    expect(item.unitPrice).toBeCloseTo(20 + 5.5 + 2 + 2 + 1.5, 5)
  })

  it('handles negative deltas and no toppings', () => {
    expect(buildCartItem(menu, pizza, 1, 1, [], 1).unitPrice).toBeCloseTo(17, 5)
  })

  it('carries ids and display names, and the key ignores topping order', () => {
    const a = buildCartItem(menu, pizza, 2, 1, [2, 1], 2)
    const b = buildCartItem(menu, pizza, 2, 1, [1, 2], 5)
    expect(a).toMatchObject({
      pizzaId: 1, pizzaName: 'Margherita', sizeId: 2, sizeName: 'Medium', crustId: 1, crustName: 'Classic',
      toppingIds: [1, 2], toppingNames: ['Cheese', 'Mushroom'], quantity: 2,
    })
    expect(a.key).toBe(b.key)
    expect(buildCartItem(menu, pizza, 3, 1, [1, 2], 1).key).not.toBe(a.key)
  })

  it('throws on an id the menu does not have', () => {
    expect(() => buildCartItem(menu, pizza, 99, 1, [], 1)).toThrow()
  })
})

describe('cartTotals', () => {
  it('is an empty cart with no fee', () => {
    expect(cartTotals([])).toEqual({ count: 0, subtotal: 0, serviceFee: 0, total: 0 })
  })

  it('sums unit x qty, adds the flat 1.00 fee, and counts quantities', () => {
    const cart = [buildCartItem(menu, pizza, 2, 1, [1], 2), buildCartItem(menu, pizza, 3, 2, [], 3)]
    // (20+0+0+2)*2 = 44 ; (20+5.5+2)*3 = 82.5
    expect(SERVICE_FEE).toBe(1)
    expect(cartTotals(cart)).toEqual({ count: 5, subtotal: 126.5, serviceFee: 1, total: 127.5 })
  })

  it('rounds to cents', () => {
    const cart = [{ ...buildCartItem(menu, pizza, 2, 1, [], 1), unitPrice: 0.1 }, { ...buildCartItem(menu, pizza, 1, 1, [], 1), unitPrice: 0.2 }]
    expect(cartTotals(cart).subtotal).toBe(0.3)
  })
})
