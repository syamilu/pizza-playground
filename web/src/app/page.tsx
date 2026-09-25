'use client'

import { useState } from 'react'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import Hero from '@/components/Hero'
import PizzaCard from '@/components/PizzaCard'
import PizzaCustomizer from '@/components/PizzaCustomizer'
import { Button } from '@/components/ui/button'
import { LoadingGrid } from '@/components/ui/loading-spinner'
import Link from 'next/link'
import type { MenuPizza } from '@/types/api'
import { useMenu } from '@/lib/useMenu'
import { ApiError } from '@/lib/api'

export default function Home() {
  const [selectedPizza, setSelectedPizza] = useState<MenuPizza | null>(null)
  const [customizerOpen, setCustomizerOpen] = useState(false)
  const { menu, loading, error } = useMenu()
  const pizzas = menu?.pizzas ?? []

  const handleCustomize = (pizza: MenuPizza) => {
    setSelectedPizza(pizza)
    setCustomizerOpen(true)
  }

  const customerFavorites = pizzas.slice(0, 4)

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col">
        <Navbar />
        <Hero />
        <section className="py-20 container mx-auto px-4 flex-1">
          <div className="text-center mb-12 animate-fade-in">
            <h2 className="text-4xl md:text-5xl font-heading font-bold mb-4">
              Customer Favorites
            </h2>
            <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
              Our most loved pizzas, handpicked by our community
            </p>
          </div>
          <LoadingGrid items={4} />
        </section>
        <Footer />
      </div>
    )
  }

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <Hero />

      <section className="py-20 container mx-auto px-4 flex-1">
        <div className="text-center mb-12 animate-fade-in">
          <h2 className="text-4xl md:text-5xl font-heading font-bold mb-4">
            Customer Favorites
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
            Our most loved pizzas, handpicked by our community
          </p>
        </div>

        {error ? (
          <p role="alert" data-testid="home-error" className="text-center py-16 text-destructive">
            {error instanceof ApiError ? error.message : 'Could not load the menu'}
          </p>
        ) : customerFavorites.length === 0 ? (
          <div className="text-center py-16">
            <div className="flex flex-col items-center gap-3 text-muted-foreground/50">
              <svg xmlns="http://www.w3.org/2000/svg" className="w-16 h-16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9-4.03-9-9-9zm0 0v18M3 12h18" />
              </svg>
              <p className="text-lg font-medium">No pizzas available yet</p>
              <p className="text-sm">Check back soon — we're cooking something great!</p>
            </div>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 md:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-6 lg:gap-8 animate-scale-in">
              {customerFavorites.map((pizza) => (
                <PizzaCard
                  key={pizza.id}
                  pizza={pizza}
                  onCustomize={handleCustomize}
                />
              ))}
            </div>
            <div className="mt-10 text-center">
              <Link href="/menu">
                <Button className="btn-primary px-8 py-4 text-base font-heading">View all</Button>
              </Link>
            </div>
          </>
        )}
      </section>

      <Footer />

      <PizzaCustomizer
        pizza={selectedPizza}
        open={customizerOpen}
        onClose={() => setCustomizerOpen(false)}
      />
    </div>
  )
}
