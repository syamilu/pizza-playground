import Link from 'next/link'
import { Pizza } from 'lucide-react'

export default function Footer() {
  return (
    <footer className="mt-8 sm:mt-16 bg-muted/30 py-8 border-t border-border">
      <div className="container mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-4 text-sm text-muted-foreground">
        <Link href="/" className="flex items-center gap-2 font-heading font-bold text-foreground">
          <Pizza className="h-5 w-5 text-primary" />
          Pizza Playground
        </Link>
        <nav className="flex gap-4">
          <Link href="/menu" className="hover:text-primary">Menu</Link>
          <Link href="/login" className="hover:text-primary">Sign in</Link>
        </nav>
        <p>A practice target for testing. Not a real shop.</p>
      </div>
    </footer>
  )
}
