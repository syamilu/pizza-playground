import { describe, it, expect } from 'vitest'
import { buildOrderPayload, type CartItem } from '@/lib/checkout'

const item = (over: Partial<CartItem>): CartItem => ({
  key: 'k',
  pizzaId: 1,
  pizzaName: 'Margherita',
  sizeId: 2,
  sizeName: 'Medium',
  crustId: 1,
  crustName: 'Classic',
  toppingIds: [],
  toppingNames: [],
  unitPrice: 20,
  quantity: 1,
  ...over,
})

describe('buildOrderPayload', () => {
  const customer = { name: 'Ana', email: 'ana@example.com', phone: '0123456789' }

  it('maps two cart items to ids and quantities, autoPay false, type from the form', () => {
    const cart = [
      item({ key: 'a', pizzaId: 1, sizeId: 2, crustId: 1, toppingIds: [3, 4], quantity: 2 }),
      item({ key: 'b', pizzaId: 5, sizeId: 3, crustId: 2, toppingIds: [], quantity: 1 }),
    ]
    const payload = buildOrderPayload(cart, customer, 'PICKUP')

    expect(payload.items).toHaveLength(2)
    expect(payload.items[0]).toEqual({ pizzaId: 1, sizeId: 2, crustId: 1, toppingIds: [3, 4], quantity: 2 })
    expect(payload.items[1]).toEqual({ pizzaId: 5, sizeId: 3, crustId: 2, toppingIds: [], quantity: 1 })
    expect(payload.autoPay).toBe(false)
    expect(payload.type).toBe('PICKUP')
    expect(payload.customer).toEqual(customer)
  })

  it('carries DELIVERY through and sends no display fields', () => {
    const payload = buildOrderPayload([item({})], customer, 'DELIVERY')
    expect(payload.type).toBe('DELIVERY')
    expect(Object.keys(payload.items[0]).sort()).toEqual(['crustId', 'pizzaId', 'quantity', 'sizeId', 'toppingIds'])
  })
})
