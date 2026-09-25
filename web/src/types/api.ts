// Mirrors the Spring Boot API records (api/src/main/java/com/playground/pizza/**). Backend is authoritative.
// BigDecimal fields arrive as JSON numbers; Instant fields as ISO-8601 strings; UUIDs as strings.

export type Role = 'CUSTOMER' | 'OWNER' | 'CASHIER'
export type OrderType = 'PICKUP' | 'DELIVERY'
export type OrderStatus = 'PENDING' | 'NEW' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED'
export type PaymentStatus = 'UNPAID' | 'PAID' | 'FAILED'

export interface User {
  id: number
  email: string
  displayName: string
  role: Role
}

export interface AuthResponse {
  token: string
  user: User
}

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export interface MenuPizza {
  id: number
  name: string
  description: string
  basePrice: number
  category: string
  imageUrl: string | null
}

export interface MenuOption {
  id: number
  name: string
  priceDelta: number
}

export interface Menu {
  pizzas: MenuPizza[]
  sizes: MenuOption[]
  crusts: MenuOption[]
  toppings: MenuOption[]
}

export interface CreateOrderItem {
  pizzaId: number
  sizeId: number
  crustId: number
  toppingIds: number[]
  quantity: number
}

export interface CreateOrderRequest {
  type: OrderType
  customer: { name: string; email: string; phone: string }
  items: CreateOrderItem[]
  autoPay: boolean
}

export interface CreateOrderResponse {
  orderId: string
  orderNumber: number
  payUrl: string | null
}

export interface OrderItemView {
  pizzaName: string
  size: string
  crust: string
  toppings: string[]
  quantity: number
  linePrice: number
}

export interface OrderView {
  id: string
  orderNumber: number
  status: OrderStatus
  paymentStatus: PaymentStatus
  type: OrderType
  customerName: string
  customerEmail: string
  customerPhone: string
  subtotal: number
  serviceFee: number
  total: number
  createdAt: string
  paidAt: string | null
  items: OrderItemView[]
}

export interface PaymentStatusView {
  orderId: string
  paymentStatus: PaymentStatus
  status: OrderStatus
  orderNumber: number
}
