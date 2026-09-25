'use client'

import { Suspense, useEffect, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LoadingSpinner } from '@/components/ui/loading-spinner'
import { ApiError, getOrder, paymentStatus } from '@/lib/api'
import { formatCurrency } from '@/lib/utils'
import type { OrderView, PaymentStatus } from '@/types/api'

const POLL_MS = 1000
const MAX_POLLS = 60

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Phase = 'waiting' | 'paid' | 'failed' | 'timeout' | 'not-found'

function Confirmation() {
  const raw = useSearchParams().get('orderId')
  const orderId = raw && UUID_RE.test(raw) ? raw : null
  const [phase, setPhase] = useState<Phase>('waiting')
  const [status, setStatus] = useState<PaymentStatus | null>(null)
  const [order, setOrder] = useState<OrderView | null>(null)
  const [run, setRun] = useState(0) // bumped by the refresh button to restart polling

  useEffect(() => {
    if (!orderId) return
    let cancelled = false
    let timer: ReturnType<typeof setTimeout>
    setPhase('waiting')

    const poll = async (attempt: number) => {
      try {
        const s = await paymentStatus(orderId)
        if (cancelled) return
        setStatus(s.paymentStatus)
        if (s.paymentStatus === 'PAID') {
          const o = await getOrder(orderId)
          if (!cancelled) {
            setOrder(o)
            setPhase('paid')
          }
          return
        }
        if (s.paymentStatus === 'FAILED') return setPhase('failed')
      } catch (e) {
        if (cancelled) return
        if (e instanceof ApiError && (e.status === 404 || e.status === 400)) return setPhase('not-found')
        // anything else (network blip, 5xx): keep polling until the deadline
      }
      if (attempt + 1 >= MAX_POLLS) setPhase('timeout')
      else timer = setTimeout(() => poll(attempt + 1), POLL_MS)
    }
    poll(0)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [orderId, run])

  if (!orderId || phase === 'not-found') {
    return (
      <p data-testid="order-not-found">
        We could not find that order.{' '}
        <Link href="/menu" className="text-primary underline">
          Back to the menu
        </Link>
      </p>
    )
  }

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle className="font-heading">
          {phase === 'paid' ? 'Thank you, your order is in!' : phase === 'failed' ? 'Payment failed' : 'Confirming your payment'}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Payment status: <span data-testid="payment-status" className="font-semibold">{status ?? 'CHECKING'}</span>
        </p>

        {phase === 'waiting' && <LoadingSpinner text="Waiting for the payment gateway..." />}

        {phase === 'timeout' && (
          <div className="space-y-3" data-testid="payment-timeout">
            <p>Still waiting for the payment to be confirmed.</p>
            <Button onClick={() => setRun((n) => n + 1)} data-testid="payment-refresh">
              Refresh
            </Button>
          </div>
        )}

        {phase === 'failed' && (
          <div className="space-y-3" data-testid="payment-failed">
            <p>Your payment did not go through. Nothing was charged.</p>
            <Link href="/menu">
              <Button>Back to the menu</Button>
            </Link>
          </div>
        )}

        {phase === 'paid' && order && (
          <div className="space-y-3">
            <p className="text-lg">
              Order number <span data-testid="order-number" className="font-heading font-bold">{order.orderNumber}</span>
            </p>
            <ul className="space-y-2 text-sm" data-testid="order-items">
              {order.items.map((item, i) => (
                <li key={i} className="flex justify-between gap-4">
                  <span>
                    {item.quantity}x {item.pizzaName}{' '}
                    <span className="text-muted-foreground">
                      ({item.size}, {item.crust}
                      {item.toppings.length > 0 && `, + ${item.toppings.join(', ')}`})
                    </span>
                  </span>
                  <span className="font-semibold shrink-0">{formatCurrency(item.linePrice)}</span>
                </li>
              ))}
            </ul>
            <div className="border-t border-border pt-3 flex justify-between font-heading font-bold text-lg">
              <span>Total</span>
              <span data-testid="order-total">{formatCurrency(order.total)}</span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export default function OrderConfirmationPage() {
  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main id="main-content" className="container mx-auto px-4 py-16 flex-1">
        <h1 className="text-3xl font-heading font-bold mb-8">Order Confirmation</h1>
        <Suspense fallback={<LoadingSpinner text="Loading..." />}>
          <Confirmation />
        </Suspense>
      </main>
      <Footer />
    </div>
  )
}
