'use client'

import { useCallback, useEffect, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { LoadingSpinner } from '@/components/ui/loading-spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/contexts/AuthContext'
import { ApiError, orderHistory } from '@/lib/api'
import { defaultHistoryRange } from '@/lib/kanban'
import { formatCurrency } from '@/lib/utils'
import type { OrderView } from '@/types/api'

export default function OrderHistoryPage() {
  const { user } = useAuth()
  const [range, setRange] = useState(defaultHistoryRange)
  const [orders, setOrders] = useState<OrderView[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [denied, setDenied] = useState(false)
  const forbidden = user?.role === 'CASHIER' || denied

  const load = useCallback(async (from: string, to: string) => {
    setLoading(true)
    setError(null)
    try {
      setOrders(await orderHistory(from, to))
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) setDenied(true)
      else setError(e instanceof ApiError ? e.message : 'Could not load order history')
    } finally {
      setLoading(false)
    }
  }, [])

  // Initial load with the default range; CASHIER never calls the OWNER-only endpoint.
  useEffect(() => {
    if (user && user.role !== 'CASHIER') load(range.from, range.to)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, load])

  if (forbidden) {
    return (
      <p role="alert" data-testid="history-forbidden" className="text-muted-foreground">
        Owner access required
      </p>
    )
  }

  return (
    <>
      <h1 className="text-3xl font-heading font-bold mb-6">Order history</h1>
      <form
        className="flex flex-wrap items-end gap-4 mb-6"
        onSubmit={(e) => {
          e.preventDefault()
          load(range.from, range.to)
        }}
      >
        <div className="space-y-1">
          <Label htmlFor="history-from">From</Label>
          <Input
            id="history-from"
            type="date"
            value={range.from}
            max={range.to}
            required
            onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
            data-testid="history-from"
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="history-to">To</Label>
          <Input
            id="history-to"
            type="date"
            value={range.to}
            min={range.from}
            required
            onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
            data-testid="history-to"
          />
        </div>
        <Button type="submit" disabled={loading} data-testid="history-load">Load</Button>
      </form>
      {error && <p role="alert" data-testid="history-error" className="text-destructive mb-4">{error}</p>}
      {loading && !orders ? (
        <LoadingSpinner text="Loading history..." />
      ) : orders && (
        <Table data-testid="history-table">
          <TableHeader>
            <TableRow>
              <TableHead>Order</TableHead>
              <TableHead>Date</TableHead>
              <TableHead>Customer</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Total</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">No orders in this range</TableCell>
              </TableRow>
            ) : (
              orders.map((o) => (
                <TableRow key={o.id} data-testid="history-row" data-order-number={o.orderNumber}>
                  <TableCell>#{o.orderNumber}</TableCell>
                  <TableCell>{new Date(o.createdAt).toLocaleString()}</TableCell>
                  <TableCell>{o.customerName}</TableCell>
                  <TableCell>
                    <Badge variant={o.status === 'COMPLETED' ? 'default' : 'secondary'}>{o.status}</Badge>
                  </TableCell>
                  <TableCell className="text-right">{formatCurrency(o.total)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}
    </>
  )
}
