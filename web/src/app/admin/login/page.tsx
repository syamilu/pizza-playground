import AuthForm from '@/components/AuthForm'

export default function AdminLoginPage() {
  return <AuthForm mode="login" next="/admin/dashboard" adminOnly />
}
