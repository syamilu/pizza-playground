'use client'

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { formatCurrency } from '@/lib/utils';
import type { MenuOption, MenuPizza } from '@/types/api';
import { useMenu } from '@/lib/useMenu';
import { buildCartItem } from '@/lib/checkout';
import { useCart } from '@/contexts/CartContext';
import { toast } from 'sonner';
import { Loader2, Pizza as PizzaIcon } from 'lucide-react';

interface PizzaCustomizerProps {
  pizza: MenuPizza | null;
  open: boolean;
  onClose: () => void;
}

// Default to the zero-delta option (e.g. Medium / Classic), else the first one.
const defaultId = (options: MenuOption[]) => (options.find((o) => o.priceDelta === 0) ?? options[0])?.id ?? 0;

function OptionGroup({
  title,
  kind,
  options,
  value,
  onChange,
}: {
  title: string;
  kind: 'size' | 'crust';
  options: MenuOption[];
  value: number;
  onChange: (id: number) => void;
}) {
  return (
    <div className="mb-4">
      <h4 className="font-heading font-semibold text-base mb-3">{title}</h4>
      <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label={title}>
        {options.map((o) => (
          <Button
            key={o.id}
            type="button"
            role="radio"
            aria-checked={value === o.id}
            data-testid={`customizer-${kind}-${o.id}`}
            variant={value === o.id ? 'default' : 'outline'}
            onClick={() => onChange(o.id)}
            className="h-auto flex-col py-2"
          >
            <span className="font-medium">{o.name}</span>
            {o.priceDelta !== 0 && (
              <span className="text-xs opacity-80">
                {o.priceDelta > 0 ? '+' : '-'}
                {formatCurrency(Math.abs(o.priceDelta))}
              </span>
            )}
          </Button>
        ))}
      </div>
    </div>
  );
}

export default function PizzaCustomizer({ pizza, open, onClose }: PizzaCustomizerProps) {
  const { addToCart } = useCart();
  const { menu, error, loading } = useMenu();
  const [sizeId, setSizeId] = useState(0);
  const [crustId, setCrustId] = useState(0);
  const [toppingIds, setToppingIds] = useState<number[]>([]);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    if (open && menu) {
      setSizeId(defaultId(menu.sizes));
      setCrustId(defaultId(menu.crusts));
      setToppingIds([]);
      setQuantity(1);
    }
  }, [open, menu]);

  if (!pizza) return null;

  // null until the menu is loaded and the defaults are applied
  const preview = (() => {
    if (!menu) return null;
    try {
      return buildCartItem(menu, pizza, sizeId, crustId, toppingIds, quantity);
    } catch {
      return null;
    }
  })();

  const toggleTopping = (id: number) =>
    setToppingIds((prev) => (prev.includes(id) ? prev.filter((t) => t !== id) : [...prev, id]));

  const handleAddToCart = () => {
    if (!preview) return;
    addToCart(preview);
    toast.success(`${quantity}x ${pizza.name} added to cart!`);
    onClose();
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-4xl max-h-[85vh] sm:max-h-[90vh] overflow-y-auto bg-card">
        <DialogHeader>
          <DialogTitle className="text-3xl font-heading font-bold">
            Customize Your Pizza
          </DialogTitle>
          <DialogDescription>
            Choose your size, crust, and toppings to create your perfect pizza
          </DialogDescription>
        </DialogHeader>

        <div className="grid md:grid-cols-2 gap-6 mt-4">
          <div className="space-y-4">
            {pizza.imageUrl ? (
              <div className="aspect-square w-full overflow-hidden rounded-2xl shadow-md bg-muted">
                <img src={pizza.imageUrl} alt={pizza.name} className="w-full h-full object-cover" />
              </div>
            ) : (
              <div className="aspect-square w-full bg-muted rounded-2xl flex flex-col items-center justify-center gap-2 text-muted-foreground/40">
                <PizzaIcon className="w-16 h-16" />
                <span className="text-sm">No Image</span>
              </div>
            )}
            <div className="min-w-0">
              <h3 className="text-xl font-heading font-bold break-words">{pizza.name}</h3>
              {pizza.description && (
                <p className="text-sm text-muted-foreground mt-1 break-words line-clamp-3">{pizza.description}</p>
              )}
            </div>
          </div>

          <div className="flex flex-col">
            {loading ? (
              <div className="flex items-center justify-center py-6">
                <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
                <span className="ml-2 text-sm text-muted-foreground">Loading options...</span>
              </div>
            ) : error || !menu ? (
              <div className="rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive font-medium">
                Could not load the menu options. Close and try again.
              </div>
            ) : (
              <>
                <OptionGroup title="Size" kind="size" options={menu.sizes} value={sizeId} onChange={setSizeId} />
                <OptionGroup title="Crust" kind="crust" options={menu.crusts} value={crustId} onChange={setCrustId} />

                <div>
                  <h4 className="font-heading font-semibold text-base mb-3">Add Toppings</h4>
                  {menu.toppings.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-4">
                      No toppings available at the moment.
                    </p>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {menu.toppings.map((topping) => (
                        <div
                          key={topping.id}
                          className="flex items-center space-x-2 p-2.5 rounded-lg border border-border transition-colors hover:bg-muted"
                        >
                          <Checkbox
                            id={`topping-${topping.id}`}
                            data-testid={`customizer-topping-${topping.id}`}
                            checked={toppingIds.includes(topping.id)}
                            onCheckedChange={() => toggleTopping(topping.id)}
                          />
                          <Label htmlFor={`topping-${topping.id}`} className="flex-1 text-sm cursor-pointer">
                            <div className="flex items-center justify-between">
                              <span className="font-medium">{topping.name}</span>
                              <span className="text-primary font-semibold text-xs">
                                +{formatCurrency(topping.priceDelta)}
                              </span>
                            </div>
                          </Label>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            )}

            <div className="flex items-center justify-between border-t border-border mt-4 pt-4">
              <span className="font-heading font-semibold text-base">Quantity</span>
              <div className="flex items-center gap-3">
                <Button
                  variant="outline"
                  size="icon"
                  aria-label="Decrease quantity"
                  onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                  disabled={quantity <= 1}
                  className="h-9 w-9"
                >
                  -
                </Button>
                <span className="text-lg font-bold w-8 text-center" data-testid="customizer-quantity">
                  {quantity}
                </span>
                <Button
                  variant="outline"
                  size="icon"
                  aria-label="Increase quantity"
                  onClick={() => setQuantity((q) => q + 1)}
                  className="h-9 w-9"
                >
                  +
                </Button>
              </div>
            </div>

            <div className="mt-auto pt-4 space-y-3">
              <div className="flex items-center justify-between bg-muted/60 rounded-xl px-4 py-3">
                <span className="font-heading font-semibold text-sm text-muted-foreground uppercase tracking-wide">
                  Total
                </span>
                <span className="text-2xl font-heading font-bold text-primary" data-testid="customizer-total">
                  {formatCurrency(preview ? preview.unitPrice * quantity : 0)}
                </span>
              </div>
              <Button
                onClick={handleAddToCart}
                size="lg"
                disabled={!preview}
                data-testid="add-to-cart"
                className="w-full btn-primary text-base font-heading font-semibold py-5"
              >
                Add to Cart
              </Button>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
