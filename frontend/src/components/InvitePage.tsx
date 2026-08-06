import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getInvitation, acceptInvitation, type InvitationPreview } from '../api/invitations'
import { useAuth } from '../context/AuthContext'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'

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

interface Props {
  token: string
}

type PageState =
  | { phase: 'loading' }
  | { phase: 'invalid' }
  | { phase: 'ready'; invitation: InvitationPreview }
  | { phase: 'accepted' }

function LoginShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-svh items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="pb-2">
          <p className="text-center text-xl font-semibold tracking-tight">moneylytics</p>
        </CardHeader>
        <CardContent>{children}</CardContent>
      </Card>
    </div>
  )
}

export default function InvitePage({ token }: Props) {
  const { t } = useTranslation()
  const { username, login, register, refreshAuth } = useAuth()
  const [state, setState] = useState<PageState>({ phase: 'loading' })
  const [mode, setMode] = useState<'login' | 'register'>('register')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [formLoading, setFormLoading] = useState(false)

  useEffect(() => {
    getInvitation(token)
      .then(inv => {
        setState(inv.valid ? { phase: 'ready', invitation: inv } : { phase: 'invalid' })
        if (inv.valid) setMode('register')
      })
      .catch(() => setState({ phase: 'invalid' }))
  }, [token])

  async function handleAcceptAsLoggedIn() {
    setFormLoading(true)
    try {
      await acceptInvitation(token)
      await refreshAuth()
      setState({ phase: 'accepted' })
    } catch {
      setFormError(t('invite.acceptError'))
    } finally {
      setFormLoading(false)
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (state.phase !== 'ready') return
    const email = state.invitation.email
    setFormError(null)

    if (mode === 'register' && password !== confirmPassword) {
      setFormError(t('auth.passwordMismatch'))
      return
    }

    setFormLoading(true)
    try {
      sessionStorage.setItem('pendingInviteToken', token)
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register(email, password)
      }
      sessionStorage.removeItem('pendingInviteToken')
      setState({ phase: 'accepted' })
    } catch (err) {
      sessionStorage.removeItem('pendingInviteToken')
      const msg = err instanceof Error ? err.message : ''
      if (msg === 'Username already taken') {
        setFormError(t('auth.usernameTaken'))
        setMode('login')
      } else if (msg === 'Invalid credentials') {
        setFormError(t('auth.invalidCredentials'))
      } else {
        setFormError(t('auth.error'))
      }
    } finally {
      setFormLoading(false)
    }
  }

  function handleGoogleClick() {
    sessionStorage.setItem('pendingInviteToken', token)
    window.location.href = '/oauth2/authorization/google'
  }

  if (state.phase === 'loading') {
    return (
      <LoginShell>
        <p className="text-sm text-muted-foreground">{t('invite.loading')}</p>
      </LoginShell>
    )
  }

  if (state.phase === 'invalid') {
    return (
      <LoginShell>
        <p className="text-sm text-destructive">{t('invite.invalid')}</p>
      </LoginShell>
    )
  }

  if (state.phase === 'accepted') {
    return (
      <LoginShell>
        <div className="flex flex-col gap-3 text-center">
          <p className="text-sm">{t('invite.accepted')}</p>
          <Button render={<a href="/" />}>{t('invite.goToApp')}</Button>
        </div>
      </LoginShell>
    )
  }

  const { invitation } = state

  if (username) {
    return (
      <LoginShell>
        <div className="flex flex-col gap-4">
          <div>
            <h2 className="font-medium">{t('invite.title', { org: invitation.organizationName })}</h2>
            <p className="text-sm text-muted-foreground">{t('invite.roleLabel', { role: invitation.role.toLowerCase() })}</p>
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <Button onClick={handleAcceptAsLoggedIn} disabled={formLoading}>
            {formLoading ? '…' : t('invite.join')}
          </Button>
        </div>
      </LoginShell>
    )
  }

  return (
    <LoginShell>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div>
          <h2 className="font-medium">{t('invite.title', { org: invitation.organizationName })}</h2>
          <p className="text-sm text-muted-foreground">{t('invite.roleLabel', { role: invitation.role.toLowerCase() })}</p>
        </div>

        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label>{t('auth.username')}</Label>
            <Input type="text" value={invitation.email} readOnly />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="inv-password">{t('auth.password')}</Label>
            <Input
              id="inv-password"
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={e => setPassword(e.target.value)}
              autoFocus
            />
          </div>
          {mode === 'register' && (
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="inv-confirm">{t('auth.confirmPassword')}</Label>
              <Input
                id="inv-confirm"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
              />
            </div>
          )}
        </div>

        {formError && <p className="text-sm text-destructive">{formError}</p>}

        <Button type="submit" disabled={formLoading || !password} className="w-full">
          {formLoading ? '…' : mode === 'login' ? t('auth.signIn') : t('auth.createAccount')}
        </Button>

        <div className="flex items-center gap-2">
          <Separator className="flex-1" />
          <span className="text-xs text-muted-foreground">or</span>
          <Separator className="flex-1" />
        </div>
        <Button variant="outline" type="button" className="w-full gap-2" onClick={handleGoogleClick}>
          <GoogleIcon />
          {t('auth.signInWithGoogle')}
        </Button>

        <p className="text-center text-sm text-muted-foreground">
          {mode === 'register' ? (
            <button type="button" className="underline underline-offset-2 hover:text-foreground" onClick={() => { setMode('login'); setFormError(null) }}>
              {t('invite.alreadyHaveAccount')}
            </button>
          ) : (
            <button type="button" className="underline underline-offset-2 hover:text-foreground" onClick={() => { setMode('register'); setFormError(null) }}>
              {t('invite.createAccount')}
            </button>
          )}
        </p>
      </form>
    </LoginShell>
  )
}
