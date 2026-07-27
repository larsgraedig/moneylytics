import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Play, UserCheck, LogOut, UserMinus, UserPlus } from 'lucide-react'
import { triggerRecurringSync, listAdminUsers, adminAddMember, adminRemoveMember, type AdminUsersResponse } from '../api/admin'
import { createOrganization } from '../api/organizations'
import { useAuth } from '../context/AuthContext'

export default function AdminPage() {
  const { t } = useTranslation()
  const { impersonating, impersonate, deimpersonate } = useAuth()
  const [running, setRunning] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [usersData, setUsersData] = useState<AdminUsersResponse | null>(null)
  const [selectedUser, setSelectedUser] = useState('')
  const [impersonating_, setImpersonating_] = useState(false)
  const [newOrgName, setNewOrgName] = useState('')
  const [orgSuccess, setOrgSuccess] = useState(false)

  const [addTargetOrg, setAddTargetOrg] = useState('')
  const [addTargetUser, setAddTargetUser] = useState('')
  const [addRole, setAddRole] = useState('MEMBER')
  const [memberError, setMemberError] = useState<string | null>(null)
  const [memberSuccess, setMemberSuccess] = useState<string | null>(null)

  const allUsers = [
    ...(usersData?.organizations.flatMap(o => o.members) ?? []),
    ...(usersData?.unorganized ?? []),
  ]

  function refreshUsers() {
    listAdminUsers().then(setUsersData).catch(() => setUsersData(null))
  }

  useEffect(() => { refreshUsers() }, [])

  async function handleTrigger() {
    setRunning(true)
    setSuccess(false)
    setError(null)
    try {
      await triggerRecurringSync()
      setSuccess(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('common.requestFailed'))
    } finally {
      setRunning(false)
    }
  }

  async function handleImpersonate() {
    if (!selectedUser) return
    setImpersonating_(true)
    try {
      await impersonate(selectedUser)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('common.requestFailed'))
    } finally {
      setImpersonating_(false)
    }
  }

  async function handleDeimpersonate() {
    await deimpersonate()
    setSelectedUser('')
  }

  async function handleAdminAdd() {
    if (!addTargetOrg || !addTargetUser) return
    setMemberError(null)
    setMemberSuccess(null)
    try {
      await adminAddMember(Number(addTargetOrg), addTargetUser, addRole)
      setAddTargetUser('')
      refreshUsers()
      setMemberSuccess(t('admin.members.addSuccess'))
    } catch {
      setMemberError(t('admin.members.addError'))
    }
  }

  async function handleAdminRemove(orgId: number, externalId: string) {
    setMemberError(null)
    setMemberSuccess(null)
    try {
      await adminRemoveMember(orgId, externalId)
      refreshUsers()
    } catch {
      setMemberError(t('admin.members.removeError'))
    }
  }

  return (
    <div className="adm-page">
      {impersonating && (
        <div className="adm-impersonation-banner">
          <span>{t('admin.impersonation.active', { username: impersonating })}</span>
          <button className="adm-deimpersonate-btn" onClick={handleDeimpersonate}>
            <LogOut size={14} />
            {t('admin.impersonation.stop')}
          </button>
        </div>
      )}

      <section className="adm-section">
        <h2 className="adm-section-title">{t('admin.impersonation.title')}</h2>
        <p className="adm-description">{t('admin.impersonation.description')}</p>
        <div className="adm-action-row">
          <select
            className="adm-user-select"
            value={selectedUser}
            onChange={e => setSelectedUser(e.target.value)}
            disabled={!!impersonating}
          >
            <option value="">{t('admin.impersonation.selectUser')}</option>
            {usersData?.organizations.map(org => (
              <optgroup key={org.id} label={org.name}>
                {org.members.map(u => (
                  <option key={u} value={u}>{u}</option>
                ))}
              </optgroup>
            ))}
            {(usersData?.unorganized.length ?? 0) > 0 && (
              <optgroup label={t('admin.impersonation.noOrg')}>
                {usersData!.unorganized.map(u => (
                  <option key={u} value={u}>{u}</option>
                ))}
              </optgroup>
            )}
          </select>
          <button
            className="adm-trigger-btn"
            onClick={handleImpersonate}
            disabled={!selectedUser || !!impersonating || impersonating_}
          >
            <UserCheck size={14} />
            {t('admin.impersonation.button')}
          </button>
        </div>
      </section>

      <section className="adm-section adm-section--wide">
        <h2 className="adm-section-title">{t('admin.members.title')}</h2>
        {memberError && <p className="adm-feedback adm-feedback--error">{memberError}</p>}
        {memberSuccess && <p className="adm-feedback adm-feedback--ok">{memberSuccess}</p>}

        <div className="adm-action-row" style={{ flexWrap: 'wrap' }}>
          <select
            className="adm-user-select"
            value={addTargetUser}
            onChange={e => setAddTargetUser(e.target.value)}
          >
            <option value="">{t('admin.members.selectUser')}</option>
            {allUsers.map(u => <option key={u} value={u}>{u}</option>)}
          </select>
          <select
            className="adm-user-select"
            value={addTargetOrg}
            onChange={e => setAddTargetOrg(e.target.value)}
          >
            <option value="">{t('admin.members.selectOrg')}</option>
            {usersData?.organizations.map(o => (
              <option key={o.id} value={o.id}>{o.name}</option>
            ))}
          </select>
          <select
            className="org-role-select"
            value={addRole}
            onChange={e => setAddRole(e.target.value)}
          >
            <option value="MEMBER">Member</option>
            <option value="ADMIN">Admin</option>
            <option value="OWNER">Owner</option>
          </select>
          <button
            className="adm-trigger-btn"
            onClick={handleAdminAdd}
            disabled={!addTargetUser || !addTargetOrg}
          >
            <UserPlus size={14} />
            {t('admin.members.add')}
          </button>
        </div>

        {usersData?.organizations.map(org => (
          <div key={org.id} className="adm-org-block">
            <span className="adm-org-block-name">{org.name}</span>
            <div className="adm-org-members">
              {org.members.map(email => (
                <div key={email} className="adm-org-member-row">
                  <span className="adm-org-member-email">{email}</span>
                  <button
                    className="org-remove-btn"
                    title={t('admin.members.remove')}
                    onClick={() => handleAdminRemove(org.id, email)}
                  >
                    <UserMinus size={13} />
                  </button>
                </div>
              ))}
            </div>
          </div>
        ))}
      </section>

      <section className="adm-section">
        <h2 className="adm-section-title">{t('admin.recurring.title')}</h2>
        <p className="adm-description">{t('admin.recurring.description')}</p>
        <div className="adm-action-row">
          <button className="adm-trigger-btn" onClick={handleTrigger} disabled={running}>
            <Play size={14} />
            {running ? t('admin.recurring.triggering') : t('admin.recurring.triggerSync')}
          </button>
          {success && <span className="adm-feedback adm-feedback--ok">{t('admin.recurring.success')}</span>}
          {error && <span className="adm-feedback adm-feedback--error">{error}</span>}
        </div>
      </section>

      <section className="adm-section">
        <h2 className="adm-section-title">{t('orgs.create.title')}</h2>
        <div className="adm-action-row">
          <input
            className="acc-input"
            type="text"
            placeholder={t('orgs.create.namePlaceholder')}
            value={newOrgName}
            onChange={e => { setNewOrgName(e.target.value); setOrgSuccess(false) }}
          />
          <button
            className="adm-trigger-btn"
            disabled={!newOrgName.trim()}
            onClick={async () => {
              if (!newOrgName.trim()) return
              try {
                await createOrganization(newOrgName.trim())
                setNewOrgName('')
                setOrgSuccess(true)
                refreshUsers()
              } catch {
                setOrgSuccess(false)
              }
            }}
          >
            {t('orgs.create.button')}
          </button>
          {orgSuccess && <span className="adm-feedback adm-feedback--ok">{t('orgs.create.success')}</span>}
        </div>
      </section>
    </div>
  )
}
