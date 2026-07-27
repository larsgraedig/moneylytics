import { fetchWithUser } from './client'

export interface InvitationPreview {
  organizationName: string
  email: string
  role: string
  expiresAt: string
  valid: boolean
}

export interface CreatedInvitation {
  token: string
  link: string
  expiresAt: string
}

export async function getInvitation(token: string): Promise<InvitationPreview> {
  const res = await fetch(`/invitations/${token}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<InvitationPreview>
}

export async function acceptInvitation(token: string): Promise<void> {
  const res = await fetchWithUser(`/invitations/${token}/accept`, { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export interface PendingInvitation {
  email: string
  role: string
  token: string
  expiresAt: string
}

export async function listPendingInvitations(orgId: number): Promise<PendingInvitation[]> {
  const res = await fetchWithUser(`/organizations/${orgId}/invitations`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<PendingInvitation[]>
}

export async function createInvitation(orgId: number, email: string, role: string): Promise<CreatedInvitation> {
  const res = await fetchWithUser(`/organizations/${orgId}/invitations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, role }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CreatedInvitation>
}
