import { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { UserMinus, Copy, Check } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { getMembers, removeMember, updateMemberRole, uploadOrgLogo, deleteOrgLogo, type OrgMember } from '../api/organizations'
import { createInvitation, listPendingInvitations, type PendingInvitation } from '../api/invitations'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Separator } from '@/components/ui/separator'

const selectCls = 'rounded-lg border border-input bg-input/30 px-3 py-2 text-sm outline-none focus:border-ring'

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
    Promise.all([getMembers(activeOrganization.id), listPendingInvitations(activeOrganization.id)])
      .then(([m, inv]) => { setMembers(m); setPendingInvitations(inv) })
      .catch(() => setError(t('orgs.errors.loadMembers')))
      .finally(() => setLoading(false))
  }, [activeOrganization])

  async function handleGenerateLink() {
    if (!activeOrganization || !inviteEmail.trim()) return
    setError(null); setSuccess(null); setInviteLink(null)
    try {
      const result = await createInvitation(activeOrganization.id, inviteEmail.trim(), inviteRole)
      const link = `${window.location.origin}${result.link}`
      setInviteLink(link)
      setInviteEmail('')
      setPendingInvitations(prev => [...prev, { email: inviteEmail.trim(), role: inviteRole, token: result.token, expiresAt: result.expiresAt }])
      setSuccess(t('orgs.invite.linkGenerated'))
    } catch { setError(t('orgs.invite.error')) }
  }

  async function handleCopy(token: string) {
    const link = `${window.location.origin}/invite/${token}`
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(link)
    } else {
      const el = document.createElement('textarea')
      el.value = link; el.style.position = 'fixed'; el.style.opacity = '0'
      document.body.appendChild(el); el.select(); document.execCommand('copy'); document.body.removeChild(el)
    }
    setCopiedToken(token)
    setTimeout(() => setCopiedToken(null), 2000)
  }

  async function handleRemove(userId: number) {
    if (!activeOrganization) return
    setError(null); setSuccess(null)
    try { await removeMember(activeOrganization.id, userId); setMembers(prev => prev.filter(m => m.userId !== userId)) } catch { setError(t('orgs.errors.removeMember')) }
  }

  async function handleRoleChange(userId: number, role: string) {
    if (!activeOrganization) return
    setError(null); setSuccess(null)
    try { await updateMemberRole(activeOrganization.id, userId, role); setMembers(prev => prev.map(m => m.userId === userId ? { ...m, role } : m)) } catch { setError(t('orgs.errors.updateRole')) }
  }

  async function handleUploadLogo() {
    if (!activeOrganization || !logoFile) return
    setError(null); setSuccess(null); setLogoLoading(true)
    try { await uploadOrgLogo(activeOrganization.id, logoFile); setLogoFile(null); if (fileInputRef.current) fileInputRef.current.value = ''; await refreshAuth(); setSuccess(t('orgs.logo.uploadSuccess')) } catch { setError(t('orgs.logo.uploadError')) } finally { setLogoLoading(false) }
  }

  async function handleRemoveLogo() {
    if (!activeOrganization) return
    setError(null); setSuccess(null); setLogoLoading(true)
    try { await deleteOrgLogo(activeOrganization.id); await refreshAuth(); setSuccess(t('orgs.logo.removeSuccess')) } catch { setError(t('orgs.logo.removeError')) } finally { setLogoLoading(false) }
  }

  const isAdminOrOwner = activeOrganization?.role === 'ADMIN' || activeOrganization?.role === 'OWNER'

  if (!activeOrganization) return null

  return (
    <div className="flex flex-col gap-6 p-6 max-w-2xl">
      {error && <p className="text-sm text-destructive">{error}</p>}
      {success && <p className="text-sm text-green-600">{success}</p>}

      {isAdminOrOwner && (
        <>
          <section className="flex flex-col gap-3">
            <h2 className="text-sm font-medium">{t('orgs.logo.title')}</h2>
            <div className="flex items-center gap-4">
              <Avatar size="lg">
                {activeOrganization.logoUrl && <AvatarImage src={activeOrganization.logoUrl} alt={activeOrganization.name} />}
                <AvatarFallback>
                  {activeOrganization.name.trim().split(/\s+/).slice(0, 2).map(w => w.charAt(0).toUpperCase()).join('')}
                </AvatarFallback>
              </Avatar>
              <div className="flex flex-wrap items-center gap-2">
                <input ref={fileInputRef} type="file" accept="image/*" className="text-sm" onChange={e => setLogoFile(e.target.files?.[0] ?? null)} />
                <Button size="sm" onClick={handleUploadLogo} disabled={!logoFile || logoLoading}>{t('orgs.logo.upload')}</Button>
                {activeOrganization.logoUrl && (
                  <Button size="sm" variant="destructive" onClick={handleRemoveLogo} disabled={logoLoading}>{t('orgs.logo.remove')}</Button>
                )}
              </div>
            </div>
          </section>
          <Separator />
        </>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium">{t('orgs.members.title')} — {activeOrganization.name}</h2>
        {loading ? <p className="text-sm text-muted-foreground">…</p> : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('orgs.members.email')}</TableHead>
                <TableHead>{t('orgs.members.role')}</TableHead>
                <TableHead className="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {members.map(m => {
                const isSelf = m.email === username
                return (
                  <TableRow key={m.userId}>
                    <TableCell className="text-sm">{m.email}</TableCell>
                    <TableCell>
                      <select className={selectCls} value={m.role} disabled={isSelf} onChange={e => handleRoleChange(m.userId, e.target.value)}>
                        <option value="MEMBER">Member</option>
                        <option value="ADMIN">Admin</option>
                        <option value="OWNER">Owner</option>
                      </select>
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon-xs" onClick={() => handleRemove(m.userId)} title={t('orgs.members.remove')} disabled={isSelf}>
                        <UserMinus className="h-3.5 w-3.5" />
                      </Button>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </section>

      <Separator />

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium">{t('orgs.invite.generateTitle')}</h2>
        <div className="flex flex-wrap items-center gap-2">
          <Input type="email" className="w-56" placeholder={t('orgs.invite.emailPlaceholder')} value={inviteEmail} onChange={e => { setInviteEmail(e.target.value); setInviteLink(null) }} />
          <select className={selectCls} value={inviteRole} onChange={e => setInviteRole(e.target.value)}>
            <option value="MEMBER">Member</option>
            <option value="ADMIN">Admin</option>
          </select>
          <Button onClick={handleGenerateLink} disabled={!inviteEmail.trim()}>{t('orgs.invite.generate')}</Button>
        </div>

        {inviteLink && (
          <div className="flex items-center gap-2">
            <Input type="text" value={inviteLink} readOnly className="flex-1 font-mono text-xs" />
            <Button variant="outline" size="sm" onClick={() => handleCopy(inviteLink.split('/invite/')[1])} title={t('orgs.invite.copy')}>
              {copiedToken === inviteLink.split('/invite/')[1] ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
            </Button>
          </div>
        )}

        {pendingInvitations.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('orgs.members.email')}</TableHead>
                <TableHead>{t('orgs.members.role')}</TableHead>
                <TableHead>{t('orgs.invite.expires')}</TableHead>
                <TableHead className="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {pendingInvitations.map(inv => (
                <TableRow key={inv.token}>
                  <TableCell className="text-sm">{inv.email}</TableCell>
                  <TableCell><Badge variant="secondary" className="text-xs">{inv.role.toLowerCase()}</Badge></TableCell>
                  <TableCell className="text-sm text-muted-foreground">{new Date(inv.expiresAt).toLocaleDateString()}</TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon-xs" onClick={() => handleCopy(inv.token)} title={t('orgs.invite.copy')}>
                      {copiedToken === inv.token ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>
    </div>
  )
}
