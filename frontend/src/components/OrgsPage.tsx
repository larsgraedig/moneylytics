import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { getMembers, addMember, removeMember, updateMemberRole, createOrganization, type OrgMember } from '../api/organizations'

export default function OrgsPage() {
  const { t } = useTranslation()
  const { activeOrganization, organizations, activateOrganization } = useAuth()
  const [members, setMembers] = useState<OrgMember[]>([])
  const [loading, setLoading] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [invitePassword, setInvitePassword] = useState('')
  const [inviteRole, setInviteRole] = useState('MEMBER')
  const [newOrgName, setNewOrgName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    if (!activeOrganization) return
    setLoading(true)
    getMembers(activeOrganization.id)
      .then(setMembers)
      .catch(() => setError('Failed to load members'))
      .finally(() => setLoading(false))
  }, [activeOrganization])

  async function handleInvite() {
    if (!activeOrganization || !inviteEmail.trim() || !invitePassword) return
    setError(null)
    try {
      await addMember(activeOrganization.id, inviteEmail.trim(), invitePassword, inviteRole)
      const updated = await getMembers(activeOrganization.id)
      setMembers(updated)
      setInviteEmail('')
      setInvitePassword('')
      setSuccess('Member invited successfully.')
    } catch {
      setError('Failed to invite member.')
    }
  }

  async function handleRemove(userId: number) {
    if (!activeOrganization) return
    setError(null)
    try {
      await removeMember(activeOrganization.id, userId)
      setMembers(prev => prev.filter(m => m.userId !== userId))
    } catch {
      setError('Failed to remove member.')
    }
  }

  async function handleRoleChange(userId: number, role: string) {
    if (!activeOrganization) return
    setError(null)
    try {
      await updateMemberRole(activeOrganization.id, userId, role)
      setMembers(prev => prev.map(m => m.userId === userId ? { ...m, role } : m))
    } catch {
      setError('Failed to update role.')
    }
  }

  async function handleCreateOrg() {
    if (!newOrgName.trim()) return
    setError(null)
    try {
      const org = await createOrganization(newOrgName.trim())
      await activateOrganization(org.id)
      setNewOrgName('')
      setSuccess('Organization created.')
    } catch {
      setError('Failed to create organization.')
    }
  }

  if (!activeOrganization) return null

  return (
    <div className="page-content">
      <h2>{t('nav.orgs')}: {activeOrganization.name}</h2>

      {error && <p className="error-msg">{error}</p>}
      {success && <p className="success-msg">{success}</p>}

      <section>
        <h3>Mitglieder</h3>
        {loading ? (
          <p>…</p>
        ) : (
          <table>
            <thead>
              <tr><th>E-Mail</th><th>Rolle</th><th></th></tr>
            </thead>
            <tbody>
              {members.map(m => (
                <tr key={m.userId}>
                  <td>{m.email}</td>
                  <td>
                    <select
                      value={m.role}
                      onChange={e => handleRoleChange(m.userId, e.target.value)}
                    >
                      <option value="MEMBER">MEMBER</option>
                      <option value="ADMIN">ADMIN</option>
                      <option value="OWNER">OWNER</option>
                    </select>
                  </td>
                  <td>
                    <button onClick={() => handleRemove(m.userId)}>Entfernen</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h3>Mitglied einladen</h3>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <input
            type="email"
            placeholder="E-Mail"
            value={inviteEmail}
            onChange={e => setInviteEmail(e.target.value)}
          />
          <input
            type="password"
            placeholder="Passwort"
            value={invitePassword}
            onChange={e => setInvitePassword(e.target.value)}
          />
          <select value={inviteRole} onChange={e => setInviteRole(e.target.value)}>
            <option value="MEMBER">MEMBER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
          <button onClick={handleInvite}>Anlegen</button>
        </div>
      </section>

      {organizations.length > 0 && (
        <section>
          <h3>Neue Organisation anlegen</h3>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <input
              type="text"
              placeholder="Name"
              value={newOrgName}
              onChange={e => setNewOrgName(e.target.value)}
            />
            <button onClick={handleCreateOrg}>Anlegen</button>
          </div>
        </section>
      )}
    </div>
  )
}
