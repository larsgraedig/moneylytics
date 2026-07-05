import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchAccounts, createAccount, updateAccount, deleteAccount, type Account } from '../api/accounts'

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
    <div className="acc-page">
      <div className="acc-add-form">
        <input
          className="acc-input"
          placeholder={t('accounts.ibanPlaceholder')}
          value={newIban}
          onChange={e => setNewIban(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleAdd()}
        />
        <input
          className="acc-input"
          placeholder={t('accounts.namePlaceholder')}
          value={newName}
          onChange={e => setNewName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleAdd()}
        />
        <button
          className="load-btn"
          onClick={handleAdd}
          disabled={adding || !newIban.trim()}
        >
          {adding ? '…' : t('accounts.addAccount')}
        </button>
        {addError && <span className="acc-error">{addError}</span>}
      </div>

      {loading && <p className="hint loading">{t('common.loading')}</p>}
      {error && <p className="hint error">{error}</p>}

      {!loading && !error && accounts.length === 0 && (
        <p className="hint">{t('accounts.empty')}</p>
      )}

      {accounts.length > 0 && (
        <div className="acc-table-wrap">
          <table className="acc-table">
            <thead>
              <tr>
                <th>{t('accounts.columns.iban')}</th>
                <th>{t('accounts.columns.name')}</th>
                <th>{t('accounts.columns.lastTransaction')}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {accounts.map(account => {
                const isEditing = editingIban === account.iban
                const isDeleting = deleting === account.iban
                return (
                  <tr key={account.iban} className="acc-row">
                    <td className="acc-cell-iban">{account.iban}</td>
                    <td className="acc-cell-name">
                      {isEditing ? (
                        <input
                          className="acc-input acc-input--inline"
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
                    </td>
                    <td className="acc-cell-last-tx">{account.lastTransactionDate ?? '—'}</td>
                    <td className="acc-cell-actions">
                      {isEditing ? (
                        <>
                          <button
                            className="acc-btn acc-btn--save"
                            onClick={() => handleSaveEdit(account.iban)}
                            disabled={saving}
                          >
                            {saving ? '…' : t('accounts.save')}
                          </button>
                          <button
                            className="acc-btn acc-btn--cancel"
                            onClick={() => setEditingIban(null)}
                          >
                            {t('accounts.cancel')}
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            className="acc-btn acc-btn--edit"
                            onClick={() => startEdit(account)}
                          >
                            {t('accounts.rename')}
                          </button>
                          <button
                            className="acc-btn acc-btn--delete"
                            onClick={() => handleDelete(account.iban)}
                            disabled={isDeleting}
                          >
                            {isDeleting ? '…' : t('accounts.delete')}
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
