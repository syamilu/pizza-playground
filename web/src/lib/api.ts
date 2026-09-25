import type {
  AuthResponse,
  CreateOrderRequest,
  CreateOrderResponse,
  Menu,
  OrderStatus,
  OrderView,
  PaymentStatusView,
  RegisterRequest,
  User,
} from '@/types/api'

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
export const TOKEN_KEY = 'pp_token'

export class ApiError extends Error {
  status: number
  requestId?: string

  constructor(status: number, message: string, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.requestId = requestId
  }
}

function storedToken(): string | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export async function api<T>(path: string, init: RequestInit & { auth?: boolean } = {}): Promise<T> {
  const { auth, ...rest } = init
  const headers = new Headers(rest.headers)
  headers.set('Accept', 'application/json')
  if (rest.body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = auth === false ? null : storedToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${API_URL}/api/v1${path}`, { ...rest, headers })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(res.status, body?.error || res.statusText || `HTTP ${res.status}`, body?.requestId)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

const post = (body: unknown) => ({ method: 'POST', body: JSON.stringify(body) })

export const getMenu = () => api<Menu>('/menu', { auth: false })
export const register = (req: RegisterRequest) => api<AuthResponse>('/auth/register', { ...post(req), auth: false })
export const login = (email: string, password: string) =>
  api<AuthResponse>('/auth/login', { ...post({ email, password }), auth: false })
export const me = () => api<User>('/auth/me')
export const createOrder = (req: CreateOrderRequest) => api<CreateOrderResponse>('/orders', post(req))
export const getOrder = (id: string) => api<OrderView>(`/orders/${encodeURIComponent(id)}`)
export const myOrders = () => api<OrderView[]>('/orders/mine')
export const paymentStatus = (orderId: string) =>
  api<PaymentStatusView>(`/payments/${encodeURIComponent(orderId)}/status`)
export const adminOrders = (status?: OrderStatus) =>
  api<OrderView[]>(`/admin/orders${status ? `?status=${status}` : ''}`)
export const updateOrderStatus = (id: string, status: OrderStatus) =>
  api<OrderView>(`/admin/orders/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
export const orderHistory = (from: string, to: string) =>
  api<OrderView[]>(`/admin/orders/history?${new URLSearchParams({ from, to })}`)
