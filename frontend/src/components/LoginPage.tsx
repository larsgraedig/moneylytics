import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation, Trans } from 'react-i18next'
import { Info } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

type LocalUserInfo = {
  username: string
  password: string
  tier: string
  role: string
  hasOrg: boolean
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path fill="#4285F4" d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615Z"/>
      <path fill="#34A853" d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18Z"/>
      <path fill="#FBBC05" d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332Z"/>
      <path fill="#EA4335" d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58Z"/>
    </svg>
  )
}

type Mode = 'login' | 'register'

export default function LoginPage() {
  const { login, register } = useAuth()
  const { t } = useTranslation()
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [localUsers, setLocalUsers] = useState<LocalUserInfo[] | null>(null)
  const [popoverOpen, setPopoverOpen] = useState(false)

  useEffect(() => {
    fetch('/auth/local-info')
      .then(r => r.ok ? r.json() : null)
      .then(data => { if (Array.isArray(data)) setLocalUsers(data) })
      .catch(() => {})
  }, [])

  function switchMode(next: Mode) {
    setMode(next)
    setError(null)
    setPassword('')
    setConfirmPassword('')
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    if (mode === 'register' && password !== confirmPassword) {
      setError(t('auth.passwordMismatch'))
      return
    }

    setLoading(true)
    try {
      if (mode === 'login') {
        await login(username, password)
      } else {
        await register(username, password)
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : ''
      if (msg === 'Invalid email') setError(t('auth.invalidEmail'))
      else if (msg === 'Username already taken') setError(t('auth.usernameTaken'))
      else if (msg === 'Registration failed') setError(t('auth.registrationFailed'))
      else if (msg === 'Invalid credentials') setError(t('auth.invalidCredentials'))
      else setError(t('auth.error'))
    } finally {
      setLoading(false)
    }
  }

  const isSubmitDisabled = loading || !username || !password || (mode === 'register' && !confirmPassword)

  return (
    <div className="flex min-h-svh items-center justify-center bg-background p-4">
      <Card className="relative w-full max-w-sm">
        {localUsers && localUsers.length > 0 && (
          <div className="absolute right-3 top-3">
            <Popover open={popoverOpen} onOpenChange={setPopoverOpen}>
              <PopoverTrigger className="flex h-6 w-6 items-center justify-center rounded text-muted-foreground hover:bg-accent hover:text-accent-foreground">
                <Info className="h-4 w-4" />
              </PopoverTrigger>
              <PopoverContent className="w-auto p-3" align="end">
                <p className="mb-2 text-xs font-medium">Demo users</p>
                <table className="text-xs">
                  <thead>
                    <tr className="text-muted-foreground">
                      <th className="pb-1 pr-3 text-left font-normal">Username</th>
                      <th className="pb-1 pr-4 text-left font-normal">Password</th>
                      <th className="pb-1 pr-3 text-left font-normal">Tier</th>
                      <th className="pb-1 pr-3 text-left font-normal">Role</th>
                      <th className="pb-1 text-left font-normal">Org</th>
                    </tr>
                  </thead>
                  <tbody>
                    {localUsers.map(u => (
                      <tr
                        key={u.username}
                        className="cursor-pointer rounded hover:bg-accent"
                        onClick={() => {
                          setUsername(u.username)
                          setPassword(u.password)
                          setPopoverOpen(false)
                        }}
                      >
                        <td className="py-0.5 pr-3 font-mono">{u.username}</td>
                        <td className="py-0.5 pr-4 font-mono">{u.password}</td>
                        <td className="py-0.5 pr-3">{u.tier}</td>
                        <td className="py-0.5 pr-3">{u.role}</td>
                        <td className="py-0.5 text-muted-foreground">{u.hasOrg ? '✓' : '–'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </PopoverContent>
            </Popover>
          </div>
        )}
        <CardHeader className="pb-2">
          <p className="text-center text-xl font-semibold tracking-tight">moneylytics</p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="username">{t('auth.username')}</Label>
                <Input
                  id="username"
                  type="email"
                  autoComplete="email"
                  autoFocus
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="password">{t('auth.password')}</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                />
              </div>
              {mode === 'register' && (
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="confirmPassword">{t('auth.confirmPassword')}</Label>
                  <Input
                    id="confirmPassword"
                    type="password"
                    autoComplete="new-password"
                    value={confirmPassword}
                    onChange={e => setConfirmPassword(e.target.value)}
                  />
                </div>
              )}
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <Button type="submit" disabled={isSubmitDisabled} className="w-full">
              {loading ? '…' : mode === 'login' ? t('auth.signIn') : t('auth.createAccount')}
            </Button>

            {mode === 'login' && (
              <>
                <div className="flex items-center gap-2">
                  <Separator className="flex-1" />
                  <span className="text-xs text-muted-foreground">or</span>
                  <Separator className="flex-1" />
                </div>
                <Button variant="outline" className="w-full gap-2" type="button" render={<a href="/oauth2/authorization/google" />}>
                  <GoogleIcon />
                  {t('auth.signInWithGoogle')}
                </Button>
              </>
            )}

            <p className="text-center text-sm text-muted-foreground">
              {mode === 'login' ? (
                <Trans i18nKey="auth.noAccount">
                  <span />
                  <button type="button" className="underline underline-offset-2 hover:text-foreground" onClick={() => switchMode('register')} />
                </Trans>
              ) : (
                <Trans i18nKey="auth.alreadyHaveAccount">
                  <span />
                  <button type="button" className="underline underline-offset-2 hover:text-foreground" onClick={() => switchMode('login')} />
                </Trans>
              )}
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
