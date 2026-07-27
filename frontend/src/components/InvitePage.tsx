import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getInvitation, acceptInvitation, type InvitationPreview } from '../api/invitations'
import { useAuth } from '../context/AuthContext'

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
      <div className="login-shell">
        <div className="login-card">
          <span className="wordmark login-wordmark">moneylytics</span>
          <p className="login-error">{t('invite.loading')}</p>
        </div>
      </div>
    )
  }

  if (state.phase === 'invalid') {
    return (
      <div className="login-shell">
        <div className="login-card">
          <span className="wordmark login-wordmark">moneylytics</span>
          <p className="login-error">{t('invite.invalid')}</p>
        </div>
      </div>
    )
  }

  if (state.phase === 'accepted') {
    return (
      <div className="login-shell">
        <div className="login-card">
          <span className="wordmark login-wordmark">moneylytics</span>
          <p style={{ color: 'var(--text)', textAlign: 'center', fontSize: 14 }}>{t('invite.accepted')}</p>
          <a className="login-btn" style={{ textDecoration: 'none', textAlign: 'center' }} href="/">{t('invite.goToApp')}</a>
        </div>
      </div>
    )
  }

  const { invitation } = state

  if (username) {
    return (
      <div className="login-shell">
        <div className="login-card">
          <span className="wordmark login-wordmark">moneylytics</span>
          <h2 className="invite-org-title">{t('invite.title', { org: invitation.organizationName })}</h2>
          <p className="invite-role-label">{t('invite.roleLabel', { role: invitation.role.toLowerCase() })}</p>
          {formError && <p className="login-error">{formError}</p>}
          <button className="login-btn" onClick={handleAcceptAsLoggedIn} disabled={formLoading}>
            {formLoading ? '…' : t('invite.join')}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={handleSubmit}>
        <span className="wordmark login-wordmark">moneylytics</span>
        <h2 className="invite-org-title">{t('invite.title', { org: invitation.organizationName })}</h2>
        <p className="invite-role-label">{t('invite.roleLabel', { role: invitation.role.toLowerCase() })}</p>

        <div className="login-fields">
          <label className="login-field">
            <span className="login-label">{t('auth.username')}</span>
            <input className="login-input" type="text" value={invitation.email} readOnly />
          </label>
          <label className="login-field">
            <span className="login-label">{t('auth.password')}</span>
            <input
              className="login-input"
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={e => setPassword(e.target.value)}
              autoFocus
            />
          </label>
          {mode === 'register' && (
            <label className="login-field">
              <span className="login-label">{t('auth.confirmPassword')}</span>
              <input
                className="login-input"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
              />
            </label>
          )}
        </div>

        {formError && <p className="login-error">{formError}</p>}

        <button className="login-btn" type="submit" disabled={formLoading || !password}>
          {formLoading ? '…' : mode === 'login' ? t('auth.signIn') : t('auth.createAccount')}
        </button>

        <div className="login-divider"><span>or</span></div>
        <button type="button" className="login-google-btn" onClick={handleGoogleClick}>
          <GoogleIcon />
          {t('auth.signInWithGoogle')}
        </button>

        <p className="login-switch">
          {mode === 'register' ? (
            <button type="button" className="login-switch-btn" onClick={() => { setMode('login'); setFormError(null) }}>
              {t('invite.alreadyHaveAccount')}
            </button>
          ) : (
            <button type="button" className="login-switch-btn" onClick={() => { setMode('register'); setFormError(null) }}>
              {t('invite.createAccount')}
            </button>
          )}
        </p>
      </form>
    </div>
  )
}
