'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LoadingSpinner } from '@/components/ui/loading-spinner'
import { useAuth } from '@/contexts/AuthContext'
import { ApiError, myOrders } from '@/lib/api'
import { formatCurrency } from '@/lib/utils'
import type { OrderView } from '@/types/api'

export default function MyOrdersPage() {
  const router = useRouter()
  const { token, authReady } = useAuth()
  const [orders, setOrders] = useState<OrderView[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!authReady) return
    if (!token) {
      router.replace('/login?next=/orders')
      return
    }
    let cancelled = false
    myOrders().then(
      (o) => !cancelled && setOrders(o),
      (e) => !cancelled && setError(e instanceof ApiError ? e.message : 'Could not load your orders'),
    )
    return () => {
      cancelled = true
    }
  }, [authReady, token, router])

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main id="main-content" className="container mx-auto px-4 py-16 flex-1">
        <h1 className="text-3xl font-heading font-bold mb-8">My orders</h1>
        {error ? (
          <p role="alert" data-testid="orders-error" className="text-destructive">{error}</p>
        ) : !orders ? (
          <LoadingSpinner text="Loading your orders..." />
        ) : orders.length === 0 ? (
          <p className="text-muted-foreground">
            No orders yet.{' '}
            <Link href="/menu" className="text-primary underline">Order a pizza</Link>
          </p>
        ) : (
          <div className="space-y-4">
            {orders.map((o) => (
              <Card key={o.id} data-testid="my-order">
                <CardHeader className="flex flex-row items-center justify-between gap-4 space-y-0">
                  <CardTitle className="font-heading text-lg">Order #{o.orderNumber}</CardTitle>
                  <div className="flex gap-2">
                    <Badge variant="outline">{o.status}</Badge>
                    <Badge variant={o.paymentStatus === 'PAID' ? 'default' : 'secondary'}>{o.paymentStatus}</Badge>
                  </div>
                </CardHeader>
                <CardContent className="text-sm space-y-1">
                  <p className="text-muted-foreground">
                    {new Date(o.createdAt).toLocaleString()} • {o.type === 'PICKUP' ? 'Pickup' : 'Delivery'}
                  </p>
                  <p>{o.items.map((i) => `${i.quantity}x ${i.pizzaName}`).join(', ')}</p>
                  <p className="font-semibold">{formatCurrency(o.total)}</p>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </main>
      <Footer />
    </div>
  )
}
