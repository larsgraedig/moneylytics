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
  refreshAuth: () => Promise<void>
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
  refreshAuth: async () => {},
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
  const orgs = data.organizations ?? []
  setOrganizations(orgs)
  // Only auto-select when exactly 1 org OR the session already remembered a specific org.
  // With multiple orgs and no session memory, leave null so OrgSelectModal can appear.
  const active =
    orgs.find(o => o.id === data.activeOrganizationId) ?? (orgs.length === 1 ? orgs[0] : null)
  setActiveOrganization(active)
}

async function processPendingInvitation(username: string | null) {
  const pendingToken = sessionStorage.getItem('pendingInviteToken')
  if (!pendingToken || !username) return
  sessionStorage.removeItem('pendingInviteToken')
  try {
    await fetch(`/invitations/${pendingToken}/accept`, { method: 'POST' })
  } catch {
    // ignore — the user will see an error if they revisit the invite page
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null)
  const [isSystemAdmin, setIsSystemAdmin] = useState(false)
  const [impersonating, setImpersonating] = useState<string | null>(null)
  const [activeOrganization, setActiveOrganization] = useState<Organization | null>(null)
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [isLoading, setIsLoading] = useState(true)

  async function loadAuth() {
    const res = await fetch('/auth/me')
    if (!res.ok) return null
    return res.json() as Promise<AuthResponse>
  }

  useEffect(() => {
    loadAuth()
      .then(async data => {
        if (data) {
          applyAuthResponse(data, setUsername, setIsSystemAdmin, setImpersonating, setActiveOrganization, setOrganizations)
          await processPendingInvitation(data.username)
          if (sessionStorage.getItem('pendingInviteToken') === null && data.username) {
            // refresh after invite acceptance to pick up new org membership
          }
        } else {
          setUsername(null)
        }
      })
      .catch(() => setUsername(null))
      .finally(() => setIsLoading(false))
  }, [])

  async function refreshAuth() {
    const data = await loadAuth()
    if (data) {
      applyAuthResponse(data, setUsername, setIsSystemAdmin, setImpersonating, setActiveOrganization, setOrganizations)
    }
  }

  async function login(user: string, password: string) {
    const res = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: user, password }),
    })
    if (!res.ok) throw new Error('Invalid credentials')
    const data = await res.json() as AuthResponse
    applyAuthResponse(data, setUsername, setIsSystemAdmin, setImpersonating, setActiveOrganization, setOrganizations)
    await processPendingInvitation(data.username)
    if (sessionStorage.getItem('pendingInviteToken') === null) {
      await refreshAuth()
    }
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
    await processPendingInvitation(data.username)
    if (sessionStorage.getItem('pendingInviteToken') === null) {
      await refreshAuth()
    }
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
        refreshAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
