import { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Play, UserCheck, LogOut, UserMinus, UserPlus } from 'lucide-react'
import { triggerRecurringSync, listAdminUsers, adminAddMember, adminRemoveMember, type AdminUsersResponse } from '../api/admin'
import { createOrganization } from '../api/organizations'
import { useAuth } from '../context/AuthContext'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'

const selectCls = 'rounded-lg border border-input bg-input/30 px-3 py-2 text-sm outline-none focus:border-ring min-w-40'

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
  const filteredUsersForAdd = usersNotInOrg.filter(u => u.toLowerCase().includes(userSearch.toLowerCase()))

  function refreshUsers() {
    listAdminUsers().then(setUsersData).catch(() => setUsersData(null))
  }

  useEffect(() => { refreshUsers() }, [])

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (userSearchRef.current && !userSearchRef.current.contains(e.target as Node)) setShowUserList(false)
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
    try { await impersonate(email) } catch (e) { setError(e instanceof Error ? e.message : t('common.requestFailed')) }
  }

  async function handleDeimpersonate() { await deimpersonate() }

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
    try { await adminRemoveMember(orgId, externalId); refreshUsers() } catch { setMemberError(t('admin.members.removeError')) }
  }

  return (
    <div className="flex flex-col gap-6 p-6 max-w-2xl">
      {impersonating && (
        <div className="flex items-center justify-between gap-4 rounded-lg border border-orange-500/30 bg-orange-500/10 px-4 py-3">
          <span className="text-sm text-orange-600">{t('admin.impersonation.active', { username: impersonating })}</span>
          <Button variant="outline" size="sm" onClick={handleDeimpersonate}>
            <LogOut className="mr-1.5 h-3.5 w-3.5" />
            {t('admin.impersonation.stop')}
          </Button>
        </div>
      )}

      <div className="flex gap-1 border-b">
        {(['members', 'recurring'] as const).map(tab => (
          <button
            key={tab}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === tab ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'}`}
            onClick={() => setActiveTab(tab)}
          >
            {t(`admin.${tab}.title`)}
          </button>
        ))}
      </div>

      {activeTab === 'members' && (
        <>
          <section className="flex flex-col gap-3">
            <h2 className="text-sm font-medium">{t('orgs.create.title')}</h2>
            <div className="flex items-center gap-2">
              <Input
                className="w-56"
                type="text"
                placeholder={t('orgs.create.namePlaceholder')}
                value={newOrgName}
                onChange={e => { setNewOrgName(e.target.value); setOrgSuccess(false) }}
              />
              <Button
                disabled={!newOrgName.trim()}
                onClick={async () => {
                  if (!newOrgName.trim()) return
                  try { await createOrganization(newOrgName.trim()); setNewOrgName(''); setOrgSuccess(true); refreshUsers() } catch { setOrgSuccess(false) }
                }}
              >
                {t('orgs.create.button')}
              </Button>
              {orgSuccess && <span className="text-sm text-green-600">{t('orgs.create.success')}</span>}
            </div>
          </section>

          <Separator />

          <section className="flex flex-col gap-3">
            <h2 className="text-sm font-medium">{t('admin.members.managementTitle')}</h2>
            {memberError && <p className="text-sm text-destructive">{memberError}</p>}
            {memberSuccess && <p className="text-sm text-green-600">{memberSuccess}</p>}

            <select className={selectCls} value={selectedOrgId} onChange={e => { setSelectedOrgId(e.target.value); setAddTargetUser(''); setUserSearch(''); setMemberError(null); setMemberSuccess(null) }}>
              <option value="">{t('admin.members.selectOrg')}</option>
              {usersData?.organizations.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
            </select>

            {selectedOrg && (
              <>
                <div className="flex flex-col gap-1 rounded-lg border p-3">
                  {orgMembers.length === 0
                    ? <p className="text-sm text-muted-foreground">{t('admin.members.noMembers')}</p>
                    : orgMembers.map(email => (
                      <div key={email} className="flex items-center justify-between py-1">
                        <span className="text-sm">{email}</span>
                        <div className="flex gap-1">
                          <Button variant="ghost" size="icon-xs" title={t('admin.impersonation.button')} onClick={() => handleImpersonate(email)} disabled={!!impersonating || email === username}>
                            <UserCheck className="h-3.5 w-3.5" />
                          </Button>
                          <Button variant="ghost" size="icon-xs" title={t('admin.members.remove')} onClick={() => handleAdminRemove(selectedOrg.id, email)} disabled={email === username}>
                            <UserMinus className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </div>
                    ))
                  }
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <div className="relative" ref={userSearchRef}>
                    <Input
                      className="w-48"
                      type="text"
                      placeholder={t('admin.members.searchUser')}
                      value={userSearch}
                      onChange={e => { setUserSearch(e.target.value); setAddTargetUser(''); setShowUserList(true) }}
                      onFocus={() => setShowUserList(true)}
                    />
                    {showUserList && filteredUsersForAdd.length > 0 && (
                      <div className="absolute top-full left-0 z-50 mt-1 w-full rounded-lg border bg-popover shadow-lg">
                        {filteredUsersForAdd.map(u => (
                          <div
                            key={u}
                            className="cursor-pointer px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
                            onMouseDown={() => { setAddTargetUser(u); setUserSearch(u); setShowUserList(false) }}
                          >
                            {u}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                  <select className={selectCls} value={addRole} onChange={e => setAddRole(e.target.value)}>
                    <option value="MEMBER">Member</option>
                    <option value="ADMIN">Admin</option>
                    <option value="OWNER">Owner</option>
                  </select>
                  <Button onClick={handleAdminAdd} disabled={!addTargetUser}>
                    <UserPlus className="mr-1.5 h-3.5 w-3.5" />
                    {t('admin.members.add')}
                  </Button>
                </div>
              </>
            )}
          </section>
        </>
      )}

      {activeTab === 'recurring' && (
        <section className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">{t('admin.recurring.description')}</p>
          <div className="flex items-center gap-3">
            <Button onClick={handleTrigger} disabled={running}>
              <Play className="mr-1.5 h-3.5 w-3.5" />
              {running ? t('admin.recurring.triggering') : t('admin.recurring.triggerSync')}
            </Button>
            {success && <Badge variant="secondary" className="text-green-600">{t('admin.recurring.success')}</Badge>}
            {error && <span className="text-sm text-destructive">{error}</span>}
          </div>
        </section>
      )}
    </div>
  )
}
