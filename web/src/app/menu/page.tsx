'use client'

import { useEffect, useState } from 'react'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import PizzaCard from '@/components/PizzaCard'
import PizzaCustomizer from '@/components/PizzaCustomizer'
import { LoadingGrid } from '@/components/ui/loading-spinner'
import { ApiError } from '@/lib/api'
import { useMenu } from '@/lib/useMenu'
import type { MenuPizza } from '@/types/api'
import { toast } from 'sonner'

export default function MenuPage() {
  const { menu, error, loading } = useMenu()
  const [selected, setSelected] = useState<MenuPizza | null>(null)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (error) toast.error(error instanceof ApiError ? error.message : 'Could not load the menu')
  }, [error])

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main id="main-content" className="container mx-auto px-4 py-16 flex-1">
        <div className="text-center mb-12">
          <h1 className="text-4xl md:text-5xl font-heading font-bold mb-4">Our Menu</h1>
          <p className="text-xl text-muted-foreground">Pick a pizza, then choose size, crust and toppings</p>
        </div>

        {loading ? (
          <LoadingGrid items={8} />
        ) : error ? (
          <p data-testid="menu-error" role="alert" className="text-center text-destructive">
            Could not load the menu. Refresh to try again.
          </p>
        ) : menu && menu.pizzas.length === 0 ? (
          <p className="text-center text-muted-foreground">No pizzas available yet.</p>
        ) : (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-6 lg:gap-8">
            {menu?.pizzas.map((pizza) => (
              <PizzaCard
                key={pizza.id}
                pizza={pizza}
                onCustomize={(p) => {
                  setSelected(p)
                  setOpen(true)
                }}
              />
            ))}
          </div>
        )}
      </main>
      <Footer />
      <PizzaCustomizer pizza={selected} open={open} onClose={() => setOpen(false)} />
    </div>
  )
}
