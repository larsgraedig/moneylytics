import { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { UserMinus, Copy, Check } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { getMembers, removeMember, updateMemberRole, uploadOrgLogo, deleteOrgLogo, type OrgMember } from '../api/organizations'
import { createInvitation, listPendingInvitations, type PendingInvitation } from '../api/invitations'

export default function OrgsPage() {
  const { t } = useTranslation()
  const { username, activeOrganization, refreshAuth } = useAuth()
  const [logoFile, setLogoFile] = useState<File | null>(null)
  const [logoLoading, setLogoLoading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [members, setMembers] = useState<OrgMember[]>([])
  const [loading, setLoading] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('MEMBER')
  const [inviteLink, setInviteLink] = useState<string | null>(null)
  const [pendingInvitations, setPendingInvitations] = useState<PendingInvitation[]>([])
  const [copiedToken, setCopiedToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    if (!activeOrganization) return
    setLoading(true)
    Promise.all([
      getMembers(activeOrganization.id),
      listPendingInvitations(activeOrganization.id),
    ])
      .then(([m, inv]) => { setMembers(m); setPendingInvitations(inv) })
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
      const link = `${window.location.origin}${result.link}`
      setInviteLink(link)
      setInviteEmail('')
      setPendingInvitations(prev => [
        ...prev,
        { email: inviteEmail.trim(), role: inviteRole, token: result.token, expiresAt: result.expiresAt },
      ])
      setSuccess(t('orgs.invite.linkGenerated'))
    } catch {
      setError(t('orgs.invite.error'))
    }
  }

  async function handleCopy(token: string) {
    const link = `${window.location.origin}/invite/${token}`
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(link)
    } else {
      const el = document.createElement('textarea')
      el.value = link
      el.style.position = 'fixed'
      el.style.opacity = '0'
      document.body.appendChild(el)
      el.select()
      document.execCommand('copy')
      document.body.removeChild(el)
    }
    setCopiedToken(token)
    setTimeout(() => setCopiedToken(null), 2000)
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

  async function handleUploadLogo() {
    if (!activeOrganization || !logoFile) return
    setError(null)
    setSuccess(null)
    setLogoLoading(true)
    try {
      await uploadOrgLogo(activeOrganization.id, logoFile)
      setLogoFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      await refreshAuth()
      setSuccess(t('orgs.logo.uploadSuccess'))
    } catch {
      setError(t('orgs.logo.uploadError'))
    } finally {
      setLogoLoading(false)
    }
  }

  async function handleRemoveLogo() {
    if (!activeOrganization) return
    setError(null)
    setSuccess(null)
    setLogoLoading(true)
    try {
      await deleteOrgLogo(activeOrganization.id)
      await refreshAuth()
      setSuccess(t('orgs.logo.removeSuccess'))
    } catch {
      setError(t('orgs.logo.removeError'))
    } finally {
      setLogoLoading(false)
    }
  }

  const isAdminOrOwner = activeOrganization?.role === 'ADMIN' || activeOrganization?.role === 'OWNER'

  if (!activeOrganization) return null

  return (
    <div className="adm-page">
      {error && <p className="adm-feedback adm-feedback--error">{error}</p>}
      {success && <p className="adm-feedback adm-feedback--ok">{success}</p>}

      {isAdminOrOwner && (
        <section className="adm-section org-section--wide">
          <h2 className="adm-section-title">{t('orgs.logo.title')}</h2>
          <div className="org-logo-row">
            {activeOrganization.logoUrl ? (
              <img
                className="org-logo-preview"
                src={activeOrganization.logoUrl}
                alt={activeOrganization.name}
              />
            ) : (
              <div className="org-logo-placeholder">
                {activeOrganization.name.trim().split(/\s+/).slice(0, 2).map(w => w.charAt(0).toUpperCase()).join('')}
              </div>
            )}
            <div className="org-logo-actions">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="org-logo-file-input"
                onChange={e => setLogoFile(e.target.files?.[0] ?? null)}
              />
              <button
                className="adm-trigger-btn"
                onClick={handleUploadLogo}
                disabled={!logoFile || logoLoading}
              >
                {t('orgs.logo.upload')}
              </button>
              {activeOrganization.logoUrl && (
                <button
                  className="org-remove-btn"
                  onClick={handleRemoveLogo}
                  disabled={logoLoading}
                >
                  {t('orgs.logo.remove')}
                </button>
              )}
            </div>
          </div>
        </section>
      )}

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

      <section className="adm-section org-section--wide">
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
            <button className="org-remove-btn invite-copy-btn" onClick={() => handleCopy(inviteLink.split('/invite/')[1])} title={t('orgs.invite.copy')}>
              {copiedToken === inviteLink.split('/invite/')[1] ? <Check size={13} /> : <Copy size={13} />}
            </button>
          </div>
        )}
        {pendingInvitations.length > 0 && (
          <table className="org-table" style={{ marginTop: 16 }}>
            <thead>
              <tr>
                <th>{t('orgs.members.email')}</th>
                <th>{t('orgs.members.role')}</th>
                <th>{t('orgs.invite.expires')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pendingInvitations.map(inv => (
                <tr key={inv.token} className="org-row">
                  <td className="org-cell-email">{inv.email}</td>
                  <td className="org-cell-role">
                    <span className="org-role-badge">{inv.role.toLowerCase()}</span>
                  </td>
                  <td className="org-cell-expires">
                    {new Date(inv.expiresAt).toLocaleDateString()}
                  </td>
                  <td className="org-cell-action">
                    <button
                      className="org-remove-btn invite-copy-btn"
                      onClick={() => handleCopy(inv.token)}
                      title={t('orgs.invite.copy')}
                    >
                      {copiedToken === inv.token ? <Check size={13} /> : <Copy size={13} />}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
