'use client'

import { useEffect } from 'react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useAuth } from '@/contexts/AuthContext'
import { Button } from '@/components/ui/button'
import { LoadingPage } from '@/components/ui/loading-spinner'

// OWNER and CASHIER only; the API enforces the same rule on /admin/**. /admin/login is the one open page.
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { user, isAdmin, authReady, logout } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const isLogin = pathname === '/admin/login'

  useEffect(() => {
    if (!isLogin && authReady && !isAdmin) router.replace('/admin/login')
  }, [isLogin, authReady, isAdmin, router])

  if (isLogin) return <>{children}</>
  if (!authReady || !isAdmin) return <LoadingPage text="Checking authentication..." />

  const link = (href: string, label: string) => (
    <Link
      href={href}
      className={pathname === href ? 'font-semibold text-primary' : 'text-muted-foreground hover:text-foreground'}
    >
      {label}
    </Link>
  )

  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b">
        <nav className="container mx-auto px-4 h-14 flex items-center gap-6 text-sm">
          <Link href="/" className="font-heading font-bold">Pizza Playground</Link>
          {link('/admin/dashboard', 'Dashboard')}
          {user?.role === 'OWNER' && link('/admin/order-history', 'History')}
          <span className="ml-auto text-muted-foreground" data-testid="admin-user">{user?.email}</span>
          <Button variant="outline" size="sm" onClick={logout} data-testid="admin-logout">Logout</Button>
        </nav>
      </header>
      <main id="main-content" className="container mx-auto px-4 py-8 flex-1">{children}</main>
    </div>
  )
}
