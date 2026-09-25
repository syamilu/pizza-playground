'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LoadingSpinner } from '@/components/ui/loading-spinner'
import { ApiError, adminOrders, updateOrderStatus } from '@/lib/api'
import { KANBAN_COLUMNS, advanceLabel, nextStatus } from '@/lib/kanban'
import { formatCurrency } from '@/lib/utils'
import type { OrderStatus, OrderView } from '@/types/api'

const POLL_MS = 5000
const TITLES: Record<(typeof KANBAN_COLUMNS)[number], string> = { NEW: 'New', PREPARING: 'Preparing', READY: 'Ready' }

const errorText = (e: unknown) => (e instanceof ApiError ? e.message : 'Could not reach the server')

export default function AdminDashboardPage() {
  const [orders, setOrders] = useState<OrderView[] | null>(null)
  const [pending, setPending] = useState<Set<string>>(new Set())
  // Bumped on every mutation so a poll that started before it cannot paint stale columns.
  const version = useRef(0)

  const load = useCallback(async () => {
    const v = version.current
    try {
      const list = await adminOrders()
      if (v === version.current) setOrders(list)
    } catch (e) {
      toast.error(errorText(e), { id: 'orders-load' }) // one toast, not one per poll
      setOrders((o) => o ?? [])
    }
  }, [])

  useEffect(() => {
    load()
    const id = setInterval(() => {
      if (document.visibilityState === 'visible') load()
    }, POLL_MS)
    return () => clearInterval(id)
  }, [load])

  const move = async (order: OrderView, status: OrderStatus) => {
    version.current++
    setPending((p) => new Set(p).add(order.id))
    // Optimistic: COMPLETED and CANCELLED leave the board.
    setOrders((list) =>
      (list ?? []).flatMap((o) =>
        o.id !== order.id ? [o] : status === 'COMPLETED' || status === 'CANCELLED' ? [] : [{ ...o, status }],
      ),
    )
    try {
      await updateOrderStatus(order.id, status)
    } catch (e) {
      toast.error(errorText(e))
      version.current++
      await load()
    } finally {
      setPending((p) => {
        const n = new Set(p)
        n.delete(order.id)
        return n
      })
    }
  }

  return (
    <>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-3xl font-heading font-bold">Orders</h1>
        <Button variant="outline" size="sm" onClick={() => load()} data-testid="refresh-orders">
          <RefreshCw className="h-4 w-4 mr-2" /> Refresh
        </Button>
      </div>
      {!orders ? (
        <LoadingSpinner text="Loading orders..." />
      ) : (
        <div className="grid gap-4 md:grid-cols-3">
          {KANBAN_COLUMNS.map((col) => {
            const cards = orders.filter((o) => o.status === col)
            return (
              <section key={col} data-testid={`kanban-column-${col}`} className="rounded-lg bg-muted/40 p-3 space-y-3">
                <h2 className="font-heading font-semibold">
                  {TITLES[col]} <span className="text-muted-foreground">({cards.length})</span>
                </h2>
                {cards.length === 0 && <p className="text-sm text-muted-foreground">No orders</p>}
                {cards.map((o) => {
                  const next = nextStatus(o.status)
                  const busy = pending.has(o.id)
                  return (
                    <Card key={o.id} data-testid="order-card" data-order-number={o.orderNumber}>
                      <CardHeader className="p-4 pb-2">
                        <CardTitle className="text-base flex justify-between">
                          <span>#{o.orderNumber}</span>
                          <span>{formatCurrency(o.total)}</span>
                        </CardTitle>
                        <p className="text-sm text-muted-foreground">
                          {o.customerName} • {o.type === 'PICKUP' ? 'Pickup' : 'Delivery'}
                        </p>
                      </CardHeader>
                      <CardContent className="p-4 pt-0 space-y-3">
                        <p className="text-sm">{o.items.map((i) => `${i.quantity}x ${i.pizzaName} (${i.size})`).join(', ')}</p>
                        <div className="flex gap-2">
                          {next && (
                            <Button size="sm" disabled={busy} onClick={() => move(o, next)} data-testid="advance-status">
                              {advanceLabel(o.status)}
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={busy}
                            onClick={() => move(o, 'CANCELLED')}
                            data-testid="cancel-order"
                          >
                            Cancel
                          </Button>
                        </div>
                      </CardContent>
                    </Card>
                  )
                })}
              </section>
            )
          })}
        </div>
      )}
    </>
  )
}
