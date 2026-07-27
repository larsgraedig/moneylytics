import { fetchWithUser } from './client'

export interface OrgMember {
  userId: number
  email: string
  role: string
}

export async function createOrganization(name: string): Promise<{ id: number; name: string; role: string }> {
  const res = await fetchWithUser('/admin/organizations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) throw new Error('Failed to create organization')
  return res.json()
}

export async function getMembers(orgId: number): Promise<OrgMember[]> {
  const res = await fetchWithUser(`/organizations/${orgId}/members`)
  if (!res.ok) throw new Error('Failed to fetch members')
  return res.json()
}

export async function addMember(orgId: number, email: string, password: string, role: string): Promise<void> {
  const res = await fetchWithUser(`/organizations/${orgId}/members`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, role }),
  })
  if (res.status === 409) throw new Error('A user with this email already exists.')
  if (!res.ok) throw new Error('Failed to add member')
}

export async function removeMember(orgId: number, userId: number): Promise<void> {
  const res = await fetchWithUser(`/organizations/${orgId}/members/${userId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('Failed to remove member')
}

export async function updateMemberRole(orgId: number, userId: number, role: string): Promise<void> {
  const res = await fetchWithUser(`/organizations/${orgId}/members/${userId}/role`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  })
  if (!res.ok) throw new Error('Failed to update member role')
}
