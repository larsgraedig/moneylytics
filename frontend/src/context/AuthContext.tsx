import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

interface AuthContextValue {
  username: string | null
  isLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  register: (username: string, password: string) => Promise<void>
}

const AuthContext = createContext<AuthContextValue>({
  username: null,
  isLoading: true,
  login: async () => {},
  logout: async () => {},
  register: async () => {},
})

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    fetch('/auth/me')
      .then(res => res.ok ? res.json() as Promise<{ username: string }> : null)
      .then(data => setUsername(data?.username ?? null))
      .catch(() => setUsername(null))
      .finally(() => setIsLoading(false))
  }, [])

  async function login(user: string, password: string) {
    const res = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: user, password }),
    })
    if (!res.ok) throw new Error('Invalid credentials')
    const data = await res.json() as { username: string }
    setUsername(data.username)
  }

  async function logout() {
    await fetch('/auth/logout', { method: 'POST' })
    setUsername(null)
  }

  async function register(user: string, password: string) {
    const res = await fetch('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: user, password }),
    })
    if (res.status === 409) throw new Error('Username already taken')
    if (!res.ok) throw new Error('Registration failed')
    const data = await res.json() as { username: string }
    setUsername(data.username)
  }

  return (
    <AuthContext.Provider value={{ username, isLoading, login, logout, register }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
