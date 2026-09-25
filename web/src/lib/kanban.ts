import type { OrderStatus } from '@/types/api'

// Mirrors the API's forward transitions; CANCELLED is a separate action on any open order.
const NEXT: Partial<Record<OrderStatus, OrderStatus>> = { NEW: 'PREPARING', PREPARING: 'READY', READY: 'COMPLETED' }
const LABEL: Partial<Record<OrderStatus, string>> = { NEW: 'Start', PREPARING: 'Ready', READY: 'Complete' }

export const KANBAN_COLUMNS = ['NEW', 'PREPARING', 'READY'] as const

export const nextStatus = (s: OrderStatus): OrderStatus | null => NEXT[s] ?? null
export const advanceLabel = (s: OrderStatus): string | null => LABEL[s] ?? null

/** Last 30 days as UTC `YYYY-MM-DD`, the same default and day boundaries the history endpoint uses. */
export function defaultHistoryRange(now = new Date()) {
  const day = (d: Date) => d.toISOString().slice(0, 10)
  return { from: day(new Date(now.getTime() - 30 * 86_400_000)), to: day(now) }
}
