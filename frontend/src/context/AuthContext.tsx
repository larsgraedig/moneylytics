import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

interface AuthContextValue {
  username: string | null
  isAdmin: boolean
  isLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  register: (username: string, password: string) => Promise<void>
}

const AuthContext = createContext<AuthContextValue>({
  username: null,
  isAdmin: false,
  isLoading: true,
  login: async () => {},
  logout: async () => {},
  register: async () => {},
})

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null)
  const [isAdmin, setIsAdmin] = useState(false)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    fetch('/auth/me')
      .then(res => res.ok ? res.json() as Promise<{ username: string; isAdmin: boolean }> : null)
      .then(data => {
        setUsername(data?.username ?? null)
        setIsAdmin(data?.isAdmin ?? false)
      })
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
    const data = await res.json() as { username: string; isAdmin: boolean }
    setUsername(data.username)
    setIsAdmin(data.isAdmin ?? false)
  }

  async function logout() {
    await fetch('/auth/logout', { method: 'POST' })
    setUsername(null)
    setIsAdmin(false)
  }

  async function register(user: string, password: string) {
    const res = await fetch('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: user, password }),
    })
    if (res.status === 409) throw new Error('Username already taken')
    if (!res.ok) throw new Error('Registration failed')
    const data = await res.json() as { username: string; isAdmin: boolean }
    setUsername(data.username)
    setIsAdmin(data.isAdmin ?? false)
  }

  return (
    <AuthContext.Provider value={{ username, isAdmin, isLoading, login, logout, register }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
