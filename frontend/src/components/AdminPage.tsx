import { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Play, UserCheck, LogOut, UserMinus, UserPlus } from 'lucide-react'
import { triggerRecurringSync, listAdminUsers, adminAddMember, adminRemoveMember, type AdminUsersResponse } from '../api/admin'
import { createOrganization } from '../api/organizations'
import { useAuth } from '../context/AuthContext'

export default function AdminPage() {
  const { t } = useTranslation()
  const { username, impersonating, impersonate, deimpersonate } = useAuth()
  const [activeTab, setActiveTab] = useState<'members' | 'recurring'>('members')
  const [running, setRunning] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [usersData, setUsersData] = useState<AdminUsersResponse | null>(null)
  const [newOrgName, setNewOrgName] = useState('')
  const [orgSuccess, setOrgSuccess] = useState(false)

  const [selectedOrgId, setSelectedOrgId] = useState('')
  const [addTargetUser, setAddTargetUser] = useState('')
  const [userSearch, setUserSearch] = useState('')
  const [showUserList, setShowUserList] = useState(false)
  const [addRole, setAddRole] = useState('MEMBER')
  const [memberError, setMemberError] = useState<string | null>(null)
  const [memberSuccess, setMemberSuccess] = useState<string | null>(null)
  const userSearchRef = useRef<HTMLDivElement>(null)

  const allUsers = [
    ...(usersData?.organizations.flatMap(o => o.members) ?? []),
    ...(usersData?.unorganized ?? []),
  ]

  const selectedOrg = usersData?.organizations.find(o => String(o.id) === selectedOrgId) ?? null
  const orgMembers = selectedOrg?.members ?? []
  const usersNotInOrg = allUsers.filter(u => !orgMembers.includes(u))
  const filteredUsersForAdd = usersNotInOrg.filter(u =>
    u.toLowerCase().includes(userSearch.toLowerCase()),
  )

  function refreshUsers() {
    listAdminUsers().then(setUsersData).catch(() => setUsersData(null))
  }

  useEffect(() => { refreshUsers() }, [])

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (userSearchRef.current && !userSearchRef.current.contains(e.target as Node)) {
        setShowUserList(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
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

  async function handleImpersonate(email: string) {
    if (!window.confirm(t('admin.impersonation.confirm', { username: email }))) return
    try {
      await impersonate(email)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('common.requestFailed'))
    }
  }

  async function handleDeimpersonate() {
    await deimpersonate()
  }

  async function handleAdminAdd() {
    if (!selectedOrgId || !addTargetUser) return
    setMemberError(null)
    setMemberSuccess(null)
    try {
      await adminAddMember(Number(selectedOrgId), addTargetUser, addRole)
      setAddTargetUser('')
      setUserSearch('')
      refreshUsers()
      setMemberSuccess(t('admin.members.addSuccess'))
    } catch {
      setMemberError(t('admin.members.addError'))
    }
  }

  async function handleAdminRemove(orgId: number, externalId: string) {
    if (!window.confirm(t('admin.members.confirmRemove', { username: externalId }))) return
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

      <div className="adm-tab-bar">
        <button
          className={`adm-tab-btn${activeTab === 'members' ? ' adm-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('members')}
        >
          {t('admin.members.title')}
        </button>
        <button
          className={`adm-tab-btn${activeTab === 'recurring' ? ' adm-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('recurring')}
        >
          {t('admin.recurring.title')}
        </button>
      </div>

      {activeTab === 'members' && (
        <>
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

          <hr className="adm-divider" />

          <section className="adm-section adm-section--wide">
            <h2 className="adm-section-title">{t('admin.members.managementTitle')}</h2>
            {memberError && <p className="adm-feedback adm-feedback--error">{memberError}</p>}
            {memberSuccess && <p className="adm-feedback adm-feedback--ok">{memberSuccess}</p>}

            <select
              className="adm-user-select"
              value={selectedOrgId}
              onChange={e => {
                setSelectedOrgId(e.target.value)
                setAddTargetUser('')
                setUserSearch('')
                setMemberError(null)
                setMemberSuccess(null)
              }}
            >
              <option value="">{t('admin.members.selectOrg')}</option>
              {usersData?.organizations.map(o => (
                <option key={o.id} value={o.id}>{o.name}</option>
              ))}
            </select>

            {selectedOrg && (
              <>
                <div className="adm-org-members" style={{ marginTop: 12 }}>
                  {orgMembers.length === 0
                    ? <p className="adm-description">{t('admin.members.noMembers')}</p>
                    : orgMembers.map(email => (
                      <div key={email} className="adm-org-member-row">
                        <span className="adm-org-member-email">{email}</span>
                        <div className="adm-org-member-actions">
                          <button
                            className="org-remove-btn"
                            title={t('admin.impersonation.button')}
                            onClick={() => handleImpersonate(email)}
                            disabled={!!impersonating || email === username}
                          >
                            <UserCheck size={13} />
                          </button>
                          <button
                            className="org-remove-btn"
                            title={t('admin.members.remove')}
                            onClick={() => handleAdminRemove(selectedOrg.id, email)}
                            disabled={email === username}
                          >
                            <UserMinus size={13} />
                          </button>
                        </div>
                      </div>
                    ))
                  }
                </div>

                <div className="adm-action-row" style={{ marginTop: 16, flexWrap: 'wrap' }}>
                  <div className="adm-user-search-wrap" ref={userSearchRef}>
                    <input
                      className="adm-user-select"
                      type="text"
                      placeholder={t('admin.members.searchUser')}
                      value={userSearch}
                      onChange={e => {
                        setUserSearch(e.target.value)
                        setAddTargetUser('')
                        setShowUserList(true)
                      }}
                      onFocus={() => setShowUserList(true)}
                    />
                    {showUserList && filteredUsersForAdd.length > 0 && (
                      <div className="adm-user-list">
                        {filteredUsersForAdd.map(u => (
                          <div
                            key={u}
                            className="adm-user-list-item"
                            onMouseDown={() => {
                              setAddTargetUser(u)
                              setUserSearch(u)
                              setShowUserList(false)
                            }}
                          >
                            {u}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
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
                    disabled={!addTargetUser}
                  >
                    <UserPlus size={14} />
                    {t('admin.members.add')}
                  </button>
                </div>
              </>
            )}
          </section>
        </>
      )}

      {activeTab === 'recurring' && (
        <section className="adm-section">
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
      )}
    </div>
  )
}
