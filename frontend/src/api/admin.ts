import { fetchWithUser } from './client'

export interface AdminOrgGroup {
  id: number
  name: string
  members: string[]
}

export interface AdminUsersResponse {
  organizations: AdminOrgGroup[]
  unorganized: string[]
}

export interface AdminTierInfo {
  id: number
  name: string
}

export interface UserTier {
  externalId: string
  tier: AdminTierInfo
}

export interface AdminTier {
  id: number
  name: string
  description: string | null
  active: boolean
  isDefault: boolean
}

export async function triggerRecurringSync(): Promise<void> {
  const res = await fetchWithUser('/admin/recurring/sync', { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function listAdminUsers(): Promise<AdminUsersResponse> {
  const res = await fetchWithUser('/admin/users')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<AdminUsersResponse>
}

export async function impersonateUser(externalId: string): Promise<void> {
  const res = await fetchWithUser(`/admin/impersonate/${encodeURIComponent(externalId)}`, { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function deimpersonateUser(): Promise<void> {
  await fetchWithUser('/admin/impersonate', { method: 'DELETE' })
}

export async function adminAddMember(orgId: number, externalId: string, role: string): Promise<void> {
  const res = await fetchWithUser(`/admin/organizations/${orgId}/members`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ externalId, role }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function adminRemoveMember(orgId: number, externalId: string): Promise<void> {
  const res = await fetchWithUser(`/admin/organizations/${orgId}/members/${encodeURIComponent(externalId)}`, {
    method: 'DELETE',
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function listAdminTiers(): Promise<AdminTier[]> {
  const res = await fetchWithUser('/admin/tiers')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<AdminTier[]>
}

export async function listUserTiers(): Promise<UserTier[]> {
  const res = await fetchWithUser('/admin/users/tiers')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<UserTier[]>
}

export async function adminAssignTier(externalId: string, tierId: number): Promise<void> {
  const res = await fetchWithUser(`/admin/users/${encodeURIComponent(externalId)}/tier`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tierId }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
