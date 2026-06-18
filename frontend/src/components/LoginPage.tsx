import { type FormEvent, useState } from 'react'
import { useTranslation, Trans } from 'react-i18next'
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
      if (msg === 'Username already taken') setError(t('auth.usernameTaken'))
      else if (msg === 'Registration failed') setError(t('auth.registrationFailed'))
      else if (msg === 'Invalid credentials') setError(t('auth.invalidCredentials'))
      else setError(t('auth.error'))
    } finally {
      setLoading(false)
    }
  }

  const isSubmitDisabled = loading || !username || !password || (mode === 'register' && !confirmPassword)

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={handleSubmit}>
        <span className="wordmark login-wordmark">moneylytics</span>

        <div className="login-fields">
          <label className="login-field">
            <span className="login-label">{t('auth.username')}</span>
            <input
              className="login-input"
              type="text"
              autoComplete="username"
              autoFocus
              value={username}
              onChange={e => setUsername(e.target.value)}
            />
          </label>
          <label className="login-field">
            <span className="login-label">{t('auth.password')}</span>
            <input
              className="login-input"
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={e => setPassword(e.target.value)}
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

        {error && <p className="login-error">{error}</p>}

        <button className="login-btn" type="submit" disabled={isSubmitDisabled}>
          {loading ? '…' : mode === 'login' ? t('auth.signIn') : t('auth.createAccount')}
        </button>

        {mode === 'login' && (
          <>
            <div className="login-divider"><span>or</span></div>
            <a className="login-google-btn" href="/oauth2/authorization/google">
              <GoogleIcon />
              {t('auth.signInWithGoogle')}
            </a>
          </>
        )}

        <p className="login-switch">
          {mode === 'login' ? (
            <Trans i18nKey="auth.noAccount">
              <span />
              <button type="button" className="login-switch-btn" onClick={() => switchMode('register')} />
            </Trans>
          ) : (
            <Trans i18nKey="auth.alreadyHaveAccount">
              <span />
              <button type="button" className="login-switch-btn" onClick={() => switchMode('login')} />
            </Trans>
          )}
        </p>
      </form>
    </div>
  )
}
