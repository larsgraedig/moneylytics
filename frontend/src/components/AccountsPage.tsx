import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchAccounts, createAccount, updateAccount, deleteAccount, type Account } from '../api/accounts'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

export default function AccountsPage() {
  const { t } = useTranslation()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [newIban, setNewIban] = useState('')
  const [newName, setNewName] = useState('')
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)
  const [editingIban, setEditingIban] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState<string | null>(null)

  useEffect(() => {
    load()
  }, [])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setAccounts(await fetchAccounts())
    } catch {
      setError(t('accounts.loadError'))
    } finally {
      setLoading(false)
    }
  }

  async function handleAdd() {
    const iban = newIban.trim()
    const name = newName.trim()
    if (!iban) return
    setAdding(true)
    setAddError(null)
    try {
      const created = await createAccount(iban, name || iban)
      setAccounts(prev => [...prev, created])
      setNewIban('')
      setNewName('')
    } catch {
      setAddError(t('accounts.addError'))
    } finally {
      setAdding(false)
    }
  }

  function startEdit(account: Account) {
    setEditingIban(account.iban)
    setEditName(account.name)
  }

  async function handleSaveEdit(iban: string) {
    const name = editName.trim()
    if (!name) return
    setSaving(true)
    try {
      const updated = await updateAccount(iban, name)
      setAccounts(prev => prev.map(a => a.iban === iban ? updated : a))
      setEditingIban(null)
    } catch {
      // silently keep editing
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(iban: string) {
    if (!confirm(t('accounts.deleteConfirm', { iban }))) return
    setDeleting(iban)
    try {
      await deleteAccount(iban)
      setAccounts(prev => prev.filter(a => a.iban !== iban))
    } catch {
      // silently ignore
    } finally {
      setDeleting(null)
    }
  }

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex flex-wrap items-end gap-2">
        <Input
          className="w-56"
          placeholder={t('accounts.ibanPlaceholder')}
          value={newIban}
          onChange={e => setNewIban(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleAdd()}
        />
        <Input
          className="w-48"
          placeholder={t('accounts.namePlaceholder')}
          value={newName}
          onChange={e => setNewName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleAdd()}
        />
        <Button onClick={handleAdd} disabled={adding || !newIban.trim()}>
          {adding ? '…' : t('accounts.addAccount')}
        </Button>
        {addError && <span className="text-sm text-destructive">{addError}</span>}
      </div>

      {loading && <p className="text-sm text-muted-foreground">{t('common.loading')}</p>}
      {error && <p className="text-sm text-destructive">{error}</p>}
      {!loading && !error && accounts.length === 0 && (
        <p className="text-sm text-muted-foreground">{t('accounts.empty')}</p>
      )}

      {accounts.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('accounts.columns.iban')}</TableHead>
              <TableHead>{t('accounts.columns.name')}</TableHead>
              <TableHead>{t('accounts.columns.lastTransaction')}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {accounts.map(account => {
              const isEditing = editingIban === account.iban
              const isDeleting = deleting === account.iban
              return (
                <TableRow key={account.iban}>
                  <TableCell className="font-mono text-xs text-muted-foreground">{account.iban}</TableCell>
                  <TableCell>
                    {isEditing ? (
                      <Input
                        className="h-7 w-40"
                        value={editName}
                        autoFocus
                        onChange={e => setEditName(e.target.value)}
                        onKeyDown={e => {
                          if (e.key === 'Enter') handleSaveEdit(account.iban)
                          if (e.key === 'Escape') setEditingIban(null)
                        }}
                      />
                    ) : (
                      account.name
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground text-sm">
                    {account.lastTransactionDate ?? '—'}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      {isEditing ? (
                        <>
                          <Button size="sm" onClick={() => handleSaveEdit(account.iban)} disabled={saving}>
                            {saving ? '…' : t('accounts.save')}
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => setEditingIban(null)}>
                            {t('accounts.cancel')}
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button size="sm" variant="ghost" onClick={() => startEdit(account)}>
                            {t('accounts.rename')}
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            onClick={() => handleDelete(account.iban)}
                            disabled={isDeleting}
                          >
                            {isDeleting ? '…' : t('accounts.delete')}
                          </Button>
                        </>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}
    </div>
  )
}
