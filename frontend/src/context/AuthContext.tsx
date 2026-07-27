import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

export interface Organization {
  id: number
  name: string
  role: string
}

interface AuthResponse {
  username: string
  isSystemAdmin: boolean
  activeOrganizationId: number | null
  organizations: Organization[]
  impersonating: string | null
}

interface AuthContextValue {
  username: string | null
  isSystemAdmin: boolean
  impersonating: string | null
  activeOrganization: Organization | null
  organizations: Organization[]
  activateOrganization: (orgId: number) => Promise<void>
  isLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  register: (username: string, password: string) => Promise<void>
  impersonate: (externalId: string) => Promise<void>
  deimpersonate: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue>({
  username: null,
  isSystemAdmin: false,
  impersonating: null,
  activeOrganization: null,
  organizations: [],
  activateOrganization: async () => {},
  isLoading: true,
  login: async () => {},
  logout: async () => {},
  register: async () => {},
  impersonate: async () => {},
  deimpersonate: async () => {},
})

function applyAuthResponse(
  data: AuthResponse,
  setUsername: (v: string | null) => void,
  setIsSystemAdmin: (v: boolean) => void,
  setImpersonating: (v: string | null) => void,
  setActiveOrganization: (v: Organization | null) => void,
  setOrganizations: (v: Organization[]) => void,
) {
  setUsername(data.username)
  setIsSystemAdmin(data.isSystemAdmin ?? false)
  setImpersonating(data.impersonating ?? null)
  setOrganizations(data.organizations ?? [])
  const orgs = data.organizations ?? []
  const active = orgs.find(o => o.id === data.activeOrganizationId) ?? orgs[0] ?? null
  setActiveOrganization(active)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null)
  const [isSystemAdmin, setIsSystemAdmin] = useState(false)
  const [impersonating, setImpersonating] = useState<string | null>(null)
  const [activeOrganization, setActiveOrganization] = useState<Organization | null>(null)
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    fetch('/auth/me')
      .then(res => res.ok ? res.json() as Promise<AuthResponse> : null)
      .then(data => {
        if (data) {
          applyAuthResponse(data, setUsername, setIsSystemAdmin, setImpersonating, setActiveOrganization, setOrganizations)
        } else {
          setUsername(null)
        }
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
    const data = await res.json() as AuthResponse
    applyAuthResponse(data, setUsername, setIsSystemAdmin, setImpersonating, setActiveOrganization, setOrganizations)
  }

  async function logout() {
    await fetch('/auth/logout', { method: 'POST' })
    setUsername(null)
    setIsSystemAdmin(false)
    setImpersonating(null)
    setActiveOrganization(null)
    setOrganizations([])
  }

  async function register(user: string, password: string) {
    const res = await fetch('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: user, password }),
    })
    if (res.status === 409) throw new Error('Username already taken')
    if (!res.ok) throw new Error('Registration failed')
    const data = await res.json() as AuthResponse
    applyAuthResponse(data, setUsername, setIsSystemAdmin, setImpersonating, setActiveOrganization, setOrganizations)
  }

  async function activateOrganization(orgId: number) {
    await fetch(`/organizations/${orgId}/activate`, { method: 'POST' })
    const org = organizations.find(o => o.id === orgId) ?? null
    setActiveOrganization(org)
  }

  async function impersonate(externalId: string) {
    const res = await fetch(`/admin/impersonate/${encodeURIComponent(externalId)}`, { method: 'POST' })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    setImpersonating(externalId)
  }

  async function deimpersonate() {
    await fetch('/admin/impersonate', { method: 'DELETE' })
    setImpersonating(null)
  }

  return (
    <AuthContext.Provider
      value={{
        username,
        isSystemAdmin,
        impersonating,
        activeOrganization,
        organizations,
        activateOrganization,
        isLoading,
        login,
        logout,
        register,
        impersonate,
        deimpersonate,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
