"use client";

import { ShoppingCart, Pizza } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { formatCurrency } from "@/lib/utils";
import { useCart } from "@/contexts/CartContext";
import { useAuth } from "@/contexts/AuthContext";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
  SheetClose,
} from "@/components/ui/sheet";

export default function Navbar() {
  const router = useRouter();
  const { items, totals, updateQuantity, removeFromCart } = useCart();
  const { user, isAdmin, logout } = useAuth();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  return (
    <nav className="sticky top-0 z-50 bg-card/95 backdrop-blur-sm border-b border-border">
      <div className="container mx-auto px-4 py-4">
        <div className="flex items-center justify-between">
          <Link href="/" className="flex items-center gap-1.5 sm:gap-2 group">
            <Pizza className="h-6 w-6 sm:h-8 sm:w-8 text-primary transition-transform group-hover:rotate-12 flex-shrink-0" />
            <span className="text-lg sm:text-2xl font-heading font-bold text-foreground">
              Pizza Playground
            </span>
          </Link>

          <div className="flex items-center gap-0 sm:gap-2">
            <Link href="/">
              <Button variant="ghost" className="text-sm sm:text-base font-medium px-2 sm:px-4">
                Home
              </Button>
            </Link>
            <Link href="/menu">
              <Button variant="ghost" className="text-sm sm:text-base font-medium px-2 sm:px-4">
                Menu
              </Button>
            </Link>

            {mounted && isAdmin && (
              <Link href="/admin/dashboard">
                <Button variant="ghost" className="text-sm sm:text-base font-medium px-2 sm:px-4">
                  Admin
                </Button>
              </Link>
            )}
            {mounted && user ? (
              <>
                <Link href="/orders">
                  <Button variant="ghost" className="text-sm sm:text-base font-medium px-2 sm:px-4">
                    My orders
                  </Button>
                </Link>
                <span data-testid="nav-user" className="hidden sm:inline text-sm text-muted-foreground px-2">
                  {user.displayName}
                </span>
                <Button
                  variant="ghost"
                  className="text-sm sm:text-base font-medium px-2 sm:px-4"
                  onClick={() => {
                    logout();
                    router.push("/");
                  }}
                >
                  Sign out
                </Button>
              </>
            ) : (
              <>
                <Link href="/login">
                  <Button variant="ghost" className="text-sm sm:text-base font-medium px-2 sm:px-4">
                    Sign in
                  </Button>
                </Link>
                <Link href="/register" className="hidden sm:inline-block">
                  <Button variant="ghost" className="text-sm sm:text-base font-medium px-2 sm:px-4">
                    Register
                  </Button>
                </Link>
              </>
            )}

            <Sheet>
              <SheetTrigger asChild>
                <Button variant="outline" size="icon" className="relative" data-testid="cart-open" aria-label="Open cart">
                  <ShoppingCart className="h-5 w-5" />
                  {mounted && totals.count > 0 && (
                    <span
                      data-testid="cart-count"
                      className="absolute -top-1.5 -right-1.5 bg-primary text-primary-foreground rounded-full w-5 h-5 flex items-center justify-center text-xs font-bold"
                    >
                      {totals.count}
                    </span>
                  )}
                </Button>
              </SheetTrigger>
              <SheetContent className="bg-card w-full sm:max-w-md flex flex-col">
                <SheetHeader className="shrink-0">
                  <SheetTitle className="font-heading">Your Cart</SheetTitle>
                  <SheetDescription>
                    {totals.count === 0
                      ? "Your cart is empty"
                      : `${totals.count} item${totals.count > 1 ? "s" : ""} in cart`}
                  </SheetDescription>
                </SheetHeader>

                <div className="relative flex-1 min-h-0">
                  <div className="max-h-[50vh] sm:max-h-[60vh] overflow-y-auto mt-8 space-y-4 pr-2 -mr-2">
                    {items.map((item) => (
                      <div key={item.key} data-testid="cart-line" className="flex gap-4 p-4 rounded-lg bg-muted">
                        <div className="flex-1 min-w-0">
                          <h3 className="font-heading font-semibold truncate">{item.pizzaName}</h3>
                          <p className="text-sm text-muted-foreground">
                            {item.sizeName} • {item.crustName}
                          </p>
                          {item.toppingNames.length > 0 && (
                            <p className="text-xs text-muted-foreground line-clamp-1">
                              + {item.toppingNames.join(", ")}
                            </p>
                          )}
                          <div className="flex items-center gap-2 mt-2">
                            <Button
                              variant="outline"
                              size="icon"
                              className="h-7 w-7"
                              aria-label="Decrease quantity"
                              onClick={() => updateQuantity(item.key, item.quantity - 1)}
                            >
                              -
                            </Button>
                            <span className="w-6 text-center text-sm font-bold">{item.quantity}</span>
                            <Button
                              variant="outline"
                              size="icon"
                              className="h-7 w-7"
                              aria-label="Increase quantity"
                              onClick={() => updateQuantity(item.key, item.quantity + 1)}
                            >
                              +
                            </Button>
                            <span className="ml-auto font-semibold">
                              {formatCurrency(item.unitPrice * item.quantity)}
                            </span>
                          </div>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => removeFromCart(item.key)}
                          className="text-destructive hover:text-destructive flex-shrink-0"
                        >
                          Remove
                        </Button>
                      </div>
                    ))}
                  </div>
                  <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-10 bg-gradient-to-t from-card to-transparent" />
                </div>

                {items.length > 0 && (
                  <div className="shrink-0 mt-4 pt-4 border-t border-border space-y-1">
                    <div className="flex justify-between text-sm text-muted-foreground">
                      <span>Subtotal</span>
                      <span>{formatCurrency(totals.subtotal)}</span>
                    </div>
                    <div className="flex justify-between text-sm text-muted-foreground">
                      <span>Service fee</span>
                      <span>{formatCurrency(totals.serviceFee)}</span>
                    </div>
                    <div className="flex justify-between items-center pb-3">
                      <span className="text-lg font-heading font-semibold">Total</span>
                      <span className="text-2xl font-heading font-bold text-primary" data-testid="cart-total">
                        {formatCurrency(totals.total)}
                      </span>
                    </div>
                    <SheetClose asChild>
                      <Button
                        className="w-full btn-primary py-6 text-lg font-heading"
                        data-testid="cart-checkout"
                        onClick={() => router.push("/checkout")}
                      >
                        Checkout
                      </Button>
                    </SheetClose>
                  </div>
                )}
              </SheetContent>
            </Sheet>
          </div>
        </div>
      </div>
    </nav>
  );
}

