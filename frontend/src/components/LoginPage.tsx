import { type FormEvent, useState } from 'react'
import { useAuth } from '../context/AuthContext'

type Mode = 'login' | 'register'

export default function LoginPage() {
  const { login, register } = useAuth()
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
      setError('Passwords do not match')
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
      setError(err instanceof Error ? err.message : 'Something went wrong')
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
            <span className="login-label">username</span>
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
            <span className="login-label">password</span>
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
              <span className="login-label">confirm password</span>
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
          {loading ? '…' : mode === 'login' ? 'sign in' : 'create account'}
        </button>

        <p className="login-switch">
          {mode === 'login' ? (
            <>no account?{' '}
              <button type="button" className="login-switch-btn" onClick={() => switchMode('register')}>
                register
              </button>
            </>
          ) : (
            <>already have one?{' '}
              <button type="button" className="login-switch-btn" onClick={() => switchMode('login')}>
                sign in
              </button>
            </>
          )}
        </p>
      </form>
    </div>
  )
}
