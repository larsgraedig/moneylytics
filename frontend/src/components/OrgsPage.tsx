import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { UserMinus } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { getMembers, addMember, removeMember, updateMemberRole, type OrgMember } from '../api/organizations'

export default function OrgsPage() {
  const { t } = useTranslation()
  const { username, activeOrganization } = useAuth()
  const [members, setMembers] = useState<OrgMember[]>([])
  const [loading, setLoading] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [invitePassword, setInvitePassword] = useState('')
  const [inviteRole, setInviteRole] = useState('MEMBER')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    if (!activeOrganization) return
    setLoading(true)
    getMembers(activeOrganization.id)
      .then(setMembers)
      .catch(() => setError(t('orgs.errors.loadMembers')))
      .finally(() => setLoading(false))
  }, [activeOrganization])

  async function handleInvite() {
    if (!activeOrganization || !inviteEmail.trim() || !invitePassword) return
    setError(null)
    setSuccess(null)
    try {
      await addMember(activeOrganization.id, inviteEmail.trim(), invitePassword, inviteRole)
      const updated = await getMembers(activeOrganization.id)
      setMembers(updated)
      setInviteEmail('')
      setInvitePassword('')
      setSuccess(t('orgs.invite.success'))
    } catch {
      setError(t('orgs.invite.error'))
    }
  }

  async function handleRemove(userId: number) {
    if (!activeOrganization) return
    setError(null)
    setSuccess(null)
    try {
      await removeMember(activeOrganization.id, userId)
      setMembers(prev => prev.filter(m => m.userId !== userId))
    } catch {
      setError(t('orgs.errors.removeMember'))
    }
  }

  async function handleRoleChange(userId: number, role: string) {
    if (!activeOrganization) return
    setError(null)
    setSuccess(null)
    try {
      await updateMemberRole(activeOrganization.id, userId, role)
      setMembers(prev => prev.map(m => m.userId === userId ? { ...m, role } : m))
    } catch {
      setError(t('orgs.errors.updateRole'))
    }
  }

  if (!activeOrganization) return null

  return (
    <div className="adm-page">
      {error && <p className="adm-feedback adm-feedback--error">{error}</p>}
      {success && <p className="adm-feedback adm-feedback--ok">{success}</p>}

      <section className="adm-section org-section--wide">
        <h2 className="adm-section-title">{t('orgs.members.title')} — {activeOrganization.name}</h2>
        {loading ? (
          <p className="adm-description">…</p>
        ) : (
          <table className="org-table">
            <thead>
              <tr>
                <th>{t('orgs.members.email')}</th>
                <th>{t('orgs.members.role')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {members.map(m => {
                const isSelf = m.email === username
                return (
                <tr key={m.userId} className="org-row">
                  <td className="org-cell-email">{m.email}</td>
                  <td className="org-cell-role">
                    <select
                      className="org-role-select"
                      value={m.role}
                      disabled={isSelf}
                      onChange={e => handleRoleChange(m.userId, e.target.value)}
                    >
                      <option value="MEMBER">Member</option>
                      <option value="ADMIN">Admin</option>
                      <option value="OWNER">Owner</option>
                    </select>
                  </td>
                  <td className="org-cell-action">
                    <button
                      className="org-remove-btn"
                      onClick={() => handleRemove(m.userId)}
                      title={t('orgs.members.remove')}
                      disabled={isSelf}
                    >
                      <UserMinus size={13} />
                    </button>
                  </td>
                </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </section>

      <section className="adm-section">
        <h2 className="adm-section-title">{t('orgs.invite.title')}</h2>
        <div className="adm-action-row" style={{ flexWrap: 'wrap' }}>
          <input
            className="acc-input"
            type="email"
            placeholder={t('orgs.invite.emailPlaceholder')}
            value={inviteEmail}
            onChange={e => setInviteEmail(e.target.value)}
          />
          <input
            className="acc-input"
            type="password"
            placeholder={t('orgs.invite.passwordPlaceholder')}
            value={invitePassword}
            onChange={e => setInvitePassword(e.target.value)}
          />
          <select
            className="org-role-select"
            value={inviteRole}
            onChange={e => setInviteRole(e.target.value)}
          >
            <option value="MEMBER">Member</option>
            <option value="ADMIN">Admin</option>
          </select>
          <button
            className="adm-trigger-btn"
            onClick={handleInvite}
            disabled={!inviteEmail.trim() || !invitePassword}
          >
            {t('orgs.invite.button')}
          </button>
        </div>
      </section>

    </div>
  )
}
