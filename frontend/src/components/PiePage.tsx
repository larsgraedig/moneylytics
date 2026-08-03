import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { ResponsivePie } from '@nivo/pie'
import { fetchCategoryTotals, type CategoryTotalItem } from '../api/transactions'
import type { SankeyNode } from '../api/transactions'
import TransactionListPanel from './TransactionListPanel'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

interface NavEntry {
  categoryId: number
  name: string
}

interface PieItem {
  id: string
  label: string
  value: number
  item: CategoryTotalItem
}

type PieDataState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; items: CategoryTotalItem[] }

export default function PiePage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [pieData, setPieData] = useState<PieDataState>({ phase: 'idle' })
  const [navStack, setNavStack] = useState<NavEntry[]>([])
  const [drilldown, setDrilldown] = useState<{ node: SankeyNode } | null>(null)

  async function loadLevel(categoryId?: number) {
    setPieData({ phase: 'loading' })
    setDrilldown(null)
    try {
      const data = await fetchCategoryTotals(from, to, iban, undefined, categoryId)
      setPieData(data.items.length === 0 ? { phase: 'idle' } : { phase: 'ready', items: data.items })
    } catch (e) {
      setPieData({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  async function load() {
    setNavStack([])
    await loadLevel()
  }

  function navigateTo(stackIndex: number) {
    const newStack = navStack.slice(0, stackIndex + 1)
    setNavStack(newStack)
    loadLevel(newStack[newStack.length - 1]?.categoryId)
  }

  function navigateToRoot() {
    setNavStack([])
    loadLevel()
  }

  function makeNode(categoryId: number, namePath: string[]): SankeyNode {
    return { name: namePath[namePath.length - 1] ?? '', value: 0, nodeKey: '', categoryId, namePath }
  }

  async function handleSliceClick(datum: any) {
    const item = datum.data.item as CategoryTotalItem
    if (item.categoryId == null) {
      // No categoryId — fallback: open transaction list using old nodeKey
      const nodeKey = navStack.length === 0
        ? `cat:${item.name}`
        : `sub:${navStack[0]?.name}:${item.name}`
      setDrilldown({ node: { name: item.name, value: 0, nodeKey, categoryId: -1, namePath: [item.name] } })
      return
    }

    const newStack = [...navStack, { categoryId: item.categoryId, name: item.name }]
    const namePath = newStack.map(e => e.name)

    // Try to load children — if empty, treat as leaf and open transaction list.
    setPieData({ phase: 'loading' })
    setDrilldown(null)
    try {
      const data = await fetchCategoryTotals(from, to, iban, undefined, item.categoryId)
      if (data.items.length === 0) {
        setPieData(pieData)  // restore previous pie (don't navigate away)
        setDrilldown({ node: makeNode(item.categoryId, namePath) })
      } else {
        setNavStack(newStack)
        setPieData({ phase: 'ready', items: data.items })
      }
    } catch (e) {
      setPieData({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  const pieItems: PieItem[] = pieData.phase === 'ready'
    ? pieData.items.map(item => ({ id: item.name, label: item.name, value: item.value, item }))
    : []

  const currentCategoryId = navStack[navStack.length - 1]?.categoryId
  const currentNamePath = navStack.map(e => e.name)

  return (
    <div className="pi-page">
      <div className="tr-controls">
        <button
          className="load-btn"
          onClick={load}
          disabled={pieData.phase === 'loading'}
        >
          {pieData.phase === 'loading' ? '…' : t('common.load')}
        </button>
      </div>

      {navStack.length > 0 && (
        <div className="pi-breadcrumb">
          <button className="pi-back-btn" onClick={navigateToRoot}>
            {t('breakdown.backToCategories')}
          </button>
          {navStack.map((entry, idx) => (
            <span key={idx}>
              <span className="pi-crumb-sep">/</span>
              {idx < navStack.length - 1 ? (
                <button className="pi-back-btn" onClick={() => navigateTo(idx)}>
                  {entry.name}
                </button>
              ) : (
                <span className="pi-crumb-current">{entry.name}</span>
              )}
            </span>
          ))}
          {currentCategoryId != null && (
            <button
              className="pi-all-btn"
              onClick={() => setDrilldown({ node: makeNode(currentCategoryId, currentNamePath) })}
            >
              {t('breakdown.allTransactions')}
            </button>
          )}
        </div>
      )}

      <div className="pi-chart-area">
        {pieData.phase === 'idle' && (
          <p className="hint"><Trans i18nKey="common.selectDateAndLoad"><span /><kbd /></Trans></p>
        )}
        {pieData.phase === 'loading' && (
          <p className="hint loading">{t('common.fetching')}</p>
        )}
        {pieData.phase === 'error' && (
          <p className="hint error">{pieData.message}</p>
        )}
        {pieData.phase === 'ready' && pieItems.length === 0 && (
          <p className="hint">{t('breakdown.noData')}</p>
        )}
        {pieData.phase === 'ready' && pieItems.length > 0 && (
          <ResponsivePie
            data={pieItems}
            innerRadius={0.55}
            padAngle={0.5}
            cornerRadius={3}
            colors={{ scheme: 'tableau10' }}
            margin={{ top: 48, right: 48, bottom: 48, left: 48 }}
            enableArcLabels={false}
            arcLinkLabelsSkipAngle={10}
            arcLinkLabelsTextColor="#6b6b78"
            arcLinkLabelsColor={{ from: 'color' }}
            arcLinkLabelsThickness={1}
            onClick={handleSliceClick}
            activeOuterRadiusOffset={6}
            tooltip={({ datum }) => (
              <div className="pi-tooltip">
                <span className="pi-tooltip-label" style={{ color: datum.color }}>{datum.label}</span>
                <span className="pi-tooltip-value">{EUR.format(datum.value)}</span>
              </div>
            )}
            theme={{
              background: 'transparent',
              text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
            }}
          />
        )}
      </div>

      {drilldown && (
        <TransactionListPanel
          node={drilldown.node}
          from={from}
          to={to}
          iban={iban}
          onClose={() => setDrilldown(null)}
        />
      )}
    </div>
  )
}
