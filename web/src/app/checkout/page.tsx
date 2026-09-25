'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useCart } from '@/contexts/CartContext'
import { useAuth } from '@/contexts/AuthContext'
import { ApiError, createOrder } from '@/lib/api'
import { buildOrderPayload } from '@/lib/checkout'
import { formatCurrency } from '@/lib/utils'
import type { OrderType } from '@/types/api'

const TYPES: OrderType[] = ['PICKUP', 'DELIVERY']

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.status === 502 ? 'Payment gateway unavailable, try again' : err.message
  return 'Could not place the order, check your connection and try again'
}

export default function CheckoutPage() {
  const router = useRouter()
  const { items, totals, clearCart } = useCart()
  const { user } = useAuth()
  const [customer, setCustomer] = useState({ name: '', email: '', phone: '' })
  const [type, setType] = useState<OrderType>('PICKUP')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Prefill once auth resolves; never overwrite what the user already typed.
  useEffect(() => {
    if (user) setCustomer((c) => ({ ...c, name: c.name || user.displayName, email: c.email || user.email }))
  }, [user])

  const field = (key: keyof typeof customer) => ({
    id: `checkout-${key}`,
    name: key,
    'data-testid': `checkout-${key}`,
    value: customer[key],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setCustomer((c) => ({ ...c, [key]: e.target.value })),
    required: true,
  })

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const res = await createOrder(buildOrderPayload(items, customer, type))
      clearCart()
      if (res.payUrl) window.location.assign(res.payUrl)
      else router.push(`/order-confirmation?orderId=${encodeURIComponent(res.orderId)}`)
    } catch (err) {
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main id="main-content" className="container mx-auto px-4 py-16 flex-1">
        <h1 className="text-3xl font-heading font-bold mb-8">Checkout</h1>

        {items.length === 0 && !submitting ? (
          <p className="text-muted-foreground">
            Your cart is empty.{' '}
            <Link href="/menu" className="text-primary underline">
              Browse the menu
            </Link>
          </p>
        ) : (
          <div className="grid lg:grid-cols-2 gap-8">
            <Card>
              <CardHeader>
                <CardTitle className="font-heading">Your details</CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={onSubmit} className="space-y-4" data-testid="checkout-form">
                  <div className="space-y-2">
                    <Label htmlFor="checkout-name">Name</Label>
                    <Input {...field('name')} autoComplete="name" maxLength={100} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="checkout-email">Email</Label>
                    <Input {...field('email')} type="email" autoComplete="email" maxLength={255} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="checkout-phone">Phone</Label>
                    <Input {...field('phone')} type="tel" autoComplete="tel" maxLength={50} />
                  </div>
                  <div className="space-y-2">
                    <span className="text-sm font-medium">Order type</span>
                    <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Order type">
                      {TYPES.map((t) => (
                        <Button
                          key={t}
                          type="button"
                          role="radio"
                          aria-checked={type === t}
                          data-testid={`checkout-type-${t}`}
                          variant={type === t ? 'default' : 'outline'}
                          onClick={() => setType(t)}
                        >
                          {t === 'PICKUP' ? 'Pickup' : 'Delivery'}
                        </Button>
                      ))}
                    </div>
                  </div>
                  {error && (
                    <p role="alert" data-testid="checkout-error" className="text-sm text-destructive">
                      {error}
                    </p>
                  )}
                  <Button
                    type="submit"
                    data-testid="checkout-submit"
                    disabled={submitting || items.length === 0}
                    className="w-full btn-primary py-6 text-lg font-heading"
                  >
                    {submitting ? 'Placing order...' : `Pay ${formatCurrency(totals.total)}`}
                  </Button>
                </form>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="font-heading">Order summary</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {items.map((item) => (
                  <div key={item.key} className="flex justify-between gap-4 text-sm">
                    <div className="min-w-0">
                      <p className="font-medium">
                        {item.quantity}x {item.pizzaName}
                      </p>
                      <p className="text-muted-foreground">
                        {item.sizeName} • {item.crustName}
                        {item.toppingNames.length > 0 && ` • + ${item.toppingNames.join(', ')}`}
                      </p>
                    </div>
                    <span className="font-semibold shrink-0">{formatCurrency(item.unitPrice * item.quantity)}</span>
                  </div>
                ))}
                <div className="border-t border-border pt-3 space-y-1 text-sm">
                  <div className="flex justify-between text-muted-foreground">
                    <span>Subtotal</span>
                    <span>{formatCurrency(totals.subtotal)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Service fee</span>
                    <span>{formatCurrency(totals.serviceFee)}</span>
                  </div>
                  <div className="flex justify-between text-lg font-heading font-bold">
                    <span>Total</span>
                    <span data-testid="checkout-total">{formatCurrency(totals.total)}</span>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        )}
      </main>
      <Footer />
    </div>
  )
}
