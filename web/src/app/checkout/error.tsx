'use client'

import { useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { RefreshCw, Home } from 'lucide-react'

interface CheckoutErrorProps {
  error: Error & { digest?: string }
  reset: () => void
}

/**
 * Next.js Error Boundary for the checkout page
 * Catches errors during rendering, data fetching, or event handlers
 */
export default function CheckoutError({ error, reset }: CheckoutErrorProps) {
  useEffect(() => {
    console.error('Checkout error:', error, { errorDigest: error.digest })
  }, [error])

  return (
    <div className="container mx-auto px-4 py-16">
      <Card className="max-w-lg mx-auto">
        <CardHeader className="text-center">
          <div className="mx-auto mb-4 text-4xl">🍕</div>
          <CardTitle className="text-2xl font-heading">
            Oops! Something went wrong
          </CardTitle>
          <CardDescription className="text-base">
            We encountered an issue while processing your checkout. Don't worry - your cart items are still saved!
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {process.env.NODE_ENV === 'development' && (
            <div className="p-3 bg-muted rounded-md">
              <p className="text-sm font-mono text-destructive break-all">
                {error.message}
              </p>
            </div>
          )}

          {error.digest && (
            <p className="text-xs text-muted-foreground text-center">
              Error ID: {error.digest}
            </p>
          )}

          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Button variant="default" onClick={reset} className="flex-1">
              <RefreshCw className="mr-2 h-4 w-4" />
              Try Again
            </Button>
            <Button
              variant="outline"
              onClick={() => (window.location.href = '/menu')}
              className="flex-1"
            >
              <Home className="mr-2 h-4 w-4" />
              Back to Menu
            </Button>
          </div>

          <p className="text-sm text-muted-foreground text-center">
            If this problem persists, please try refreshing the page or contact us for assistance.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
