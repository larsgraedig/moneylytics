import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchLinkedGroups, type LinkedGroupItem } from '../api/transactions'
import { GroupCard } from './GroupCard'

export default function LinkedTransactionsPage() {
  const { t } = useTranslation()
  const [groups, setGroups] = useState<LinkedGroupItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchLinkedGroups()
      .then(res => { setGroups(res.groups); setLoading(false) })
      .catch(e => { setError(e instanceof Error ? e.message : t('common.requestFailed')); setLoading(false) })
  }, [t])

  function handleMetaChange(groupId: number, name: string | null, comment: string | null) {
    setGroups(prev => prev.map(g => g.groupId === groupId ? { ...g, name, comment } : g))
  }

  function handleOffsetCommentChange(groupId: number, txId: number, linkId: number, comment: string | null) {
    setGroups(prev => prev.map(g => {
      if (g.groupId !== groupId) return g
      return {
        ...g,
        transactions: g.transactions.map(tx => {
          if (tx.id !== txId) return tx
          return {
            ...tx,
            offsetLinks: tx.offsetLinks.map(link => link.id === linkId ? { ...link, comment } : link),
          }
        }),
      }
    }))
  }

  if (loading) return <div className="ltx-page"><span className="ltx-status">{t('common.loading')}</span></div>
  if (error) return <div className="ltx-page"><span className="ltx-status ltx-status--error">{error}</span></div>

  return (
    <div className="ltx-page">
      <div className="ltx-header">
        <h2 className="ltx-title">{t('linked.title')}</h2>
        <span className="ltx-count">{t('linked.count', { count: groups.length })}</span>
      </div>
      {groups.length === 0
        ? <p className="ltx-status">{t('linked.empty')}</p>
        : groups.map(g => (
          <GroupCard
            key={g.groupId}
            group={g}
            onMetaChange={handleMetaChange}
            onOffsetCommentChange={handleOffsetCommentChange}
          />
        ))
      }
    </div>
  )
}
