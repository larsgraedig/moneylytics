import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { UserMinus, Copy, Check } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { getMembers, removeMember, updateMemberRole, type OrgMember } from '../api/organizations'
import { createInvitation } from '../api/invitations'

export default function OrgsPage() {
  const { t } = useTranslation()
  const { username, activeOrganization } = useAuth()
  const [members, setMembers] = useState<OrgMember[]>([])
  const [loading, setLoading] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('MEMBER')
  const [inviteLink, setInviteLink] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
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

  async function handleGenerateLink() {
    if (!activeOrganization || !inviteEmail.trim()) return
    setError(null)
    setSuccess(null)
    setInviteLink(null)
    try {
      const result = await createInvitation(activeOrganization.id, inviteEmail.trim(), inviteRole)
      setInviteLink(`${window.location.origin}${result.link}`)
      setSuccess(t('orgs.invite.linkGenerated'))
    } catch {
      setError(t('orgs.invite.error'))
    }
  }

  async function handleCopy() {
    if (!inviteLink) return
    await navigator.clipboard.writeText(inviteLink)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
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
        <h2 className="adm-section-title">{t('orgs.invite.generateTitle')}</h2>
        <div className="adm-action-row" style={{ flexWrap: 'wrap' }}>
          <input
            className="acc-input"
            type="email"
            placeholder={t('orgs.invite.emailPlaceholder')}
            value={inviteEmail}
            onChange={e => { setInviteEmail(e.target.value); setInviteLink(null) }}
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
            onClick={handleGenerateLink}
            disabled={!inviteEmail.trim()}
          >
            {t('orgs.invite.generate')}
          </button>
        </div>
        {inviteLink && (
          <div className="invite-link-row">
            <input className="acc-input invite-link-input" type="text" value={inviteLink} readOnly />
            <button className="org-remove-btn invite-copy-btn" onClick={handleCopy} title={t('orgs.invite.copy')}>
              {copied ? <Check size={13} /> : <Copy size={13} />}
            </button>
          </div>
        )}
      </section>
    </div>
  )
}
