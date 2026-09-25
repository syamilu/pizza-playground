'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/contexts/AuthContext'
import type { User } from '@/types/api'

interface AuthFormProps {
  mode: 'login' | 'register'
  /** Default destination when the URL has no `next`. */
  next?: string
  /** Staff sign-in: a CUSTOMER account is signed straight back out with an error. */
  adminOnly?: boolean
}

export default function AuthForm({ mode, next: defaultNext, adminOnly = false }: AuthFormProps) {
  const { login, register, logout } = useAuth()
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const isRegister = mode === 'register'

  const onSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const email = String(form.get('email'))
    const password = String(form.get('password'))
    setError(null)
    setSubmitting(true)
    try {
      const user: User = isRegister
        ? await register({ email, password, displayName: String(form.get('displayName')) })
        : await login(email, password)
      if (adminOnly && user.role === 'CUSTOMER') {
        logout()
        setError('not an admin account')
        setSubmitting(false)
        return
      }
      // Same-origin paths only: browsers treat `//host` and `/\host` as another origin.
      const next = new URLSearchParams(window.location.search).get('next') ?? defaultNext
      const safeNext = next && /^\/(?![\/\\])/.test(next) ? next : null
      router.push(safeNext ?? (user.role === 'CUSTOMER' ? '/menu' : '/admin/dashboard'))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
      setSubmitting(false)
    }
  }

  return (
    <>
      <Navbar />
      <main id="main-content" className="container mx-auto px-4 py-16 flex justify-center">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle className="font-heading">{isRegister ? 'Create an account' : adminOnly ? 'Staff sign in' : 'Sign in'}</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={onSubmit} className="space-y-4" data-testid={`${mode}-form`}>
              {isRegister && (
                <div className="space-y-2">
                  <Label htmlFor="displayName">Name</Label>
                  <Input id="displayName" name="displayName" autoComplete="name" required />
                </div>
              )}
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input id="email" name="email" type="email" autoComplete="email" required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  minLength={isRegister ? 8 : undefined}
                  required
                />
              </div>
              {error && (
                <p
                  role="alert"
                  data-testid={adminOnly && error === 'not an admin account' ? 'admin-login-error' : 'auth-error'}
                  className="text-sm text-destructive"
                >
                  {error}
                </p>
              )}
              <Button type="submit" className="w-full" disabled={submitting}>
                {submitting ? 'Please wait...' : isRegister ? 'Create account' : 'Sign in'}
              </Button>
            </form>
            <p className="text-sm text-muted-foreground mt-4 text-center">
              {isRegister ? (
                <>Already have an account? <Link href="/login" className="text-primary underline">Sign in</Link></>
              ) : (
                <>New here? <Link href="/register" className="text-primary underline">Create an account</Link></>
              )}
            </p>
          </CardContent>
        </Card>
      </main>
      <Footer />
    </>
  )
}
