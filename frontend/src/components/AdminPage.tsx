import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Play, UserCheck, LogOut } from 'lucide-react'
import { triggerRecurringSync, listAdminUsers, type AdminUsersResponse } from '../api/admin'
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

  useEffect(() => {
    listAdminUsers().then(setUsersData).catch(() => setUsersData(null))
  }, [])

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
