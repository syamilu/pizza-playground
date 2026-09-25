import { Button } from '@/components/ui/button';
import Link from 'next/link';

export default function Hero() {
  return (
    <section data-testid="hero" className="relative h-[400px] sm:h-[500px] md:h-[600px] flex items-center overflow-hidden bg-gradient-to-br from-primary/20 via-background to-background">
      <div className="container mx-auto px-4 relative z-10">
        <div className="max-w-2xl space-y-6 animate-fade-in">
          <h1 className="text-5xl md:text-6xl lg:text-7xl font-heading font-bold leading-tight max-w-xl">
            Pizza Playground,{' '}
            <span className="text-primary">Built for Testing</span>
          </h1>
          <p className="text-xl md:text-2xl text-muted-foreground max-w-xl">
            A practice pizza shop for performance and UI automation testing.
          </p>
          <Link href="/menu" className="inline-block mt-4 md:mt-6">
            <Button
              size="lg"
              className="btn-primary text-lg px-8 py-6 rounded-full font-heading font-semibold shadow-lg hover:shadow-xl transition-all"
            >
              Order Now
            </Button>
          </Link>
        </div>
      </div>
    </section>
  );
}
