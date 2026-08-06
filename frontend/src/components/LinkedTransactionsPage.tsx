import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchLinkedGroups, type LinkedGroupItem } from '../api/transactions'
import { GroupCard } from './GroupCard'
import { Badge } from '@/components/ui/badge'

export default function LinkedTransactionsPage() {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const [groups, setGroups] = useState<LinkedGroupItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const scrolledRef = useRef(false)

  const highlightGroupId = Number(new URLSearchParams(location.search).get('group')) || null

  useEffect(() => {
    fetchLinkedGroups()
      .then(res => { setGroups(res.groups); setLoading(false) })
      .catch(e => { setError(e instanceof Error ? e.message : t('common.requestFailed')); setLoading(false) })
  }, [t])

  useEffect(() => {
    if (!highlightGroupId || loading || scrolledRef.current) return
    const el = document.getElementById(`ltx-group-${highlightGroupId}`)
    if (!el) return
    scrolledRef.current = true
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    el.classList.add('ltx-group--highlight')
    const timer = setTimeout(() => {
      el.classList.remove('ltx-group--highlight')
      const p = new URLSearchParams(location.search)
      p.delete('group')
      navigate({ search: p.toString() }, { replace: true })
    }, 2000)
    return () => clearTimeout(timer)
  }, [highlightGroupId, loading, groups])

  function handleMetaChange(groupId: number, name: string | null, comment: string | null) {
    setGroups(prev => prev.map(g => g.groupId === groupId ? { ...g, name, comment } : g))
  }

  function handleRemoveTransaction(groupId: number, txId: number) {
    setGroups(prev => prev
      .map(g => {
        if (g.groupId !== groupId) return g
        const remaining = g.transactions.filter(tx => tx.id !== txId)
        return remaining.length >= 2 ? { ...g, transactions: remaining } : null
      })
      .filter((g): g is LinkedGroupItem => g !== null),
    )
  }

  function handleOffsetCommentChange(groupId: number, txId: number, linkId: number, comment: string | null) {
    setGroups(prev => prev.map(g => {
      if (g.groupId !== groupId) return g
      return {
        ...g,
        transactions: g.transactions.map(tx => {
          if (tx.id !== txId) return tx
          return { ...tx, offsetLinks: tx.offsetLinks.map(link => link.id === linkId ? { ...link, comment } : link) }
        }),
      }
    }))
  }

  if (loading) return <div className="flex flex-col gap-4 p-6"><p className="text-sm text-muted-foreground">{t('common.loading')}</p></div>
  if (error) return <div className="flex flex-col gap-4 p-6"><p className="text-sm text-destructive">{error}</p></div>

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex items-center gap-2">
        <h2 className="text-base font-medium">{t('linked.title')}</h2>
        <Badge variant="secondary">{t('linked.count', { count: groups.length })}</Badge>
      </div>
      {groups.length === 0
        ? <p className="text-sm text-muted-foreground">{t('linked.empty')}</p>
        : groups.map(g => (
          <div key={g.groupId} id={`ltx-group-${g.groupId}`}>
            <GroupCard
              group={g}
              onMetaChange={handleMetaChange}
              onOffsetCommentChange={handleOffsetCommentChange}
              onRemoveTransaction={txId => handleRemoveTransaction(g.groupId, txId)}
            />
          </div>
        ))
      }
    </div>
  )
}
