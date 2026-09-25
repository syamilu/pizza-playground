"use client";

import type { MenuPizza } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { formatCurrency } from "@/lib/utils";
import Image from "next/image";
import { getBlurPlaceholder } from "@/lib/blurPlaceholder";

interface PizzaCardProps {
  pizza: MenuPizza;
  onCustomize: (pizza: MenuPizza) => void;
}

export default function PizzaCard({ pizza, onCustomize }: PizzaCardProps) {
  return (
    <Card
      data-testid="menu-pizza-card"
      className="overflow-hidden card-hover border-border bg-card shadow-card hover:shadow-card-hover relative flex flex-col h-full"
    >
      <div className="aspect-square overflow-hidden relative bg-gray-100">
        {pizza.imageUrl ? (
          <Image
            src={pizza.imageUrl}
            alt={pizza.name}
            fill
            sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
            className="object-cover"
            placeholder="blur"
            blurDataURL={getBlurPlaceholder('pizza')}
            quality={85}
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center text-gray-500 text-sm">
            No Image
          </div>
        )}
      </div>
      <CardContent className="p-3 sm:p-6 space-y-1 sm:space-y-2 flex-1">
        <h3 className="text-sm sm:text-lg font-heading font-semibold text-card-foreground leading-tight line-clamp-2">
          {pizza.name}
        </h3>
        {pizza.description && (
          <p className="text-xs sm:text-sm text-muted-foreground line-clamp-1 sm:line-clamp-2">
            {pizza.description}
          </p>
        )}
        <p className="text-base sm:text-2xl font-heading font-bold text-primary">
          From {formatCurrency(pizza.basePrice)}
        </p>
      </CardContent>
      <CardFooter className="p-3 sm:p-6 pt-0">
        <Button
          onClick={() => onCustomize(pizza)}
          data-testid="customize"
          className="w-full btn-accent font-heading font-semibold text-xs sm:text-sm h-10 sm:h-11"
        >
          Customize & Order
        </Button>
      </CardFooter>
    </Card>
  );
}
