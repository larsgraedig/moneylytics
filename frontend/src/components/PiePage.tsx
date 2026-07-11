import { useMemo, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { ResponsivePie } from '@nivo/pie'
import { fetchCategoryTotals, type CategoryTotalItem } from '../api/transactions'
import TransactionListPanel from './TransactionListPanel'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

type Level = 'categories' | 'subcategories'

interface PieItem {
  id: string
  label: string
  value: number
  nodeKey: string
}

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; categoryItems: CategoryTotalItem[] }

type SubState =
  | null
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; items: CategoryTotalItem[] }

export default function PiePage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [level, setLevel] = useState<Level>('categories')
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null)
  const [subState, setSubState] = useState<SubState>(null)
  const [drilldown, setDrilldown] = useState<{ nodeKey: string } | null>(null)

  async function load() {
    setView({ phase: 'loading' })
    setLevel('categories')
    setSelectedCategory(null)
    setSubState(null)
    setDrilldown(null)
    try {
      const data = await fetchCategoryTotals(from, to, iban)
      setView(data.items.length === 0 ? { phase: 'idle' } : { phase: 'ready', categoryItems: data.items })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  const pieItems = useMemo<PieItem[]>(() => {
    if (level === 'categories') {
      if (view.phase !== 'ready') return []
      return view.categoryItems.map(item => ({
        id: item.name,
        label: item.name,
        value: item.value,
        nodeKey: `cat:${item.name}`,
      }))
    }
    if (subState?.phase !== 'ready') return []
    return subState.items.map(item => ({
      id: item.name,
      label: item.name,
      value: item.value,
      nodeKey: `sub:${selectedCategory}:${item.name}`,
    }))
  }, [view, subState, level, selectedCategory])

  function handleSliceClick(datum: any) {
    if (level === 'categories') {
      const category = datum.id as string
      setSelectedCategory(category)
      setLevel('subcategories')
      setDrilldown(null)
      setSubState({ phase: 'loading' })
      fetchCategoryTotals(from, to, iban, category)
        .then(data => setSubState({ phase: 'ready', items: data.items }))
        .catch(e => setSubState({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') }))
    } else {
      setDrilldown({ nodeKey: datum.data.nodeKey })
    }
  }

  const isSubLoading = level === 'subcategories' && subState?.phase === 'loading'
  const subError = level === 'subcategories' && subState?.phase === 'error' ? subState.message : null

  return (
    <div className="pi-page">
      <div className="tr-controls">
        <button
          className="load-btn"
          onClick={load}
          disabled={view.phase === 'loading'}
        >
          {view.phase === 'loading' ? '…' : t('common.load')}
        </button>
      </div>

      {level === 'subcategories' && selectedCategory && (
        <div className="pi-breadcrumb">
          <button
            className="pi-back-btn"
            onClick={() => { setLevel('categories'); setSelectedCategory(null); setSubState(null); setDrilldown(null) }}
          >
            {t('breakdown.backToCategories')}
          </button>
          <span className="pi-crumb-sep">/</span>
          <span className="pi-crumb-current">{selectedCategory}</span>
          <button
            className="pi-all-btn"
            onClick={() => setDrilldown({ nodeKey: `cat:${selectedCategory}` })}
          >
            {t('breakdown.allTransactions')}
          </button>
        </div>
      )}

      <div className="pi-chart-area">
        {view.phase === 'idle' && (
          <p className="hint"><Trans i18nKey="common.selectDateAndLoad"><span /><kbd /></Trans></p>
        )}
        {view.phase === 'loading' && (
          <p className="hint loading">{t('common.fetching')}</p>
        )}
        {view.phase === 'error' && (
          <p className="hint error">{view.message}</p>
        )}
        {isSubLoading && (
          <p className="hint loading">{t('common.fetching')}</p>
        )}
        {subError && (
          <p className="hint error">{subError}</p>
        )}
        {view.phase === 'ready' && !isSubLoading && !subError && pieItems.length === 0 && (
          <p className="hint">{t('breakdown.noData')}</p>
        )}
        {view.phase === 'ready' && !isSubLoading && pieItems.length > 0 && (
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
          nodeKey={drilldown.nodeKey}
          from={from}
          to={to}
          iban={iban}
          onClose={() => setDrilldown(null)}
        />
      )}
    </div>
  )
}
