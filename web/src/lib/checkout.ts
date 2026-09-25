// Pure cart/checkout helpers. The price is a preview only; the API's PricingService is authoritative.
import type { CreateOrderRequest, Menu, MenuOption, MenuPizza, OrderType } from '@/types/api'

export interface CartItem {
  /** Same pizza + size + crust + toppings => same key, so re-adding merges quantity. */
  key: string
  pizzaId: number
  pizzaName: string
  sizeId: number
  sizeName: string
  crustId: number
  crustName: string
  toppingIds: number[]
  toppingNames: string[]
  unitPrice: number
  quantity: number
}

export interface Customer {
  name: string
  email: string
  phone: string
}

/** Flat fee, mirrors PricingService.SERVICE_FEE. */
export const SERVICE_FEE = 1

const cents = (n: number) => Math.round(n * 100) / 100

function option(list: MenuOption[], id: number, what: string): MenuOption {
  const found = list.find((o) => o.id === id)
  if (!found) throw new Error(`unknown ${what} ${id}`)
  return found
}

export function buildCartItem(
  menu: Menu,
  pizza: MenuPizza,
  sizeId: number,
  crustId: number,
  toppingIds: number[],
  quantity: number,
): CartItem {
  const size = option(menu.sizes, sizeId, 'size')
  const crust = option(menu.crusts, crustId, 'crust')
  const toppings = [...toppingIds].sort((a, b) => a - b).map((id) => option(menu.toppings, id, 'topping'))
  const unitPrice = cents(
    pizza.basePrice + size.priceDelta + crust.priceDelta + toppings.reduce((sum, t) => sum + t.priceDelta, 0),
  )
  return {
    key: [pizza.id, size.id, crust.id, toppings.map((t) => t.id).join('.')].join('-'),
    pizzaId: pizza.id,
    pizzaName: pizza.name,
    sizeId: size.id,
    sizeName: size.name,
    crustId: crust.id,
    crustName: crust.name,
    toppingIds: toppings.map((t) => t.id),
    toppingNames: toppings.map((t) => t.name),
    unitPrice,
    quantity,
  }
}

export function cartTotals(cart: CartItem[]) {
  const count = cart.reduce((sum, i) => sum + i.quantity, 0)
  const subtotal = cents(cart.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0))
  const serviceFee = count > 0 ? SERVICE_FEE : 0
  return { count, subtotal, serviceFee, total: cents(subtotal + serviceFee) }
}

export function buildOrderPayload(cart: CartItem[], customer: Customer, type: OrderType): CreateOrderRequest {
  return {
    type,
    customer: { name: customer.name, email: customer.email, phone: customer.phone },
    items: cart.map(({ pizzaId, sizeId, crustId, toppingIds, quantity }) => ({
      pizzaId,
      sizeId,
      crustId,
      toppingIds,
      quantity,
    })),
    autoPay: false,
  }
}
