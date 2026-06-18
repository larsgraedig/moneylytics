import { useMemo, useState } from 'react'
import { ResponsivePie } from '@nivo/pie'
import { fetchSankeyData, type SankeyResponse } from '../api/transactions'
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
  | { phase: 'ready'; data: SankeyResponse }

export default function PiePage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [level, setLevel] = useState<Level>('categories')
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null)
  const [drilldown, setDrilldown] = useState<{ nodeKey: string } | null>(null)

  async function load() {
    setView({ phase: 'loading' })
    setLevel('categories')
    setSelectedCategory(null)
    setDrilldown(null)
    try {
      const data = await fetchSankeyData(from, to, iban)
      setView(data.nodes.length === 0 ? { phase: 'idle' } : { phase: 'ready', data })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  const pieItems = useMemo<PieItem[]>(() => {
    if (view.phase !== 'ready') return []
    if (level === 'categories') {
      return view.data.nodes
        .filter(n => n.nodeKey.startsWith('cat:'))
        .map(n => ({ id: n.name, label: n.name, value: n.value, nodeKey: n.nodeKey }))
        .filter(d => d.value > 0)
        .sort((a, b) => b.value - a.value)
    }
    const prefix = `sub:${selectedCategory}:`
    return view.data.nodes
      .filter(n => n.nodeKey.startsWith(prefix))
      .map(n => {
        const sub = n.nodeKey.slice(prefix.length)
        return { id: sub, label: sub, value: n.value, nodeKey: n.nodeKey }
      })
      .filter(d => d.value > 0)
      .sort((a, b) => b.value - a.value)
  }, [view, level, selectedCategory])

  function handleSliceClick(datum: any) {
    if (level === 'categories') {
      setSelectedCategory(datum.id)
      setLevel('subcategories')
      setDrilldown(null)
    } else {
      setDrilldown({ nodeKey: datum.data.nodeKey })
    }
  }

  return (
    <div className="pi-page">
      <div className="tr-controls">
        <button
          className="load-btn"
          onClick={load}
          disabled={view.phase === 'loading'}
        >
          {view.phase === 'loading' ? '…' : 'load'}
        </button>
      </div>

      {level === 'subcategories' && selectedCategory && (
        <div className="pi-breadcrumb">
          <button
            className="pi-back-btn"
            onClick={() => { setLevel('categories'); setSelectedCategory(null); setDrilldown(null) }}
          >
            ← categories
          </button>
          <span className="pi-crumb-sep">/</span>
          <span className="pi-crumb-current">{selectedCategory}</span>
          <button
            className="pi-all-btn"
            onClick={() => setDrilldown({ nodeKey: `cat:${selectedCategory}` })}
          >
            all transactions
          </button>
        </div>
      )}

      <div className="pi-chart-area">
        {view.phase === 'idle' && (
          <p className="hint">select a date range and press <kbd>load</kbd></p>
        )}
        {view.phase === 'loading' && (
          <p className="hint loading">fetching…</p>
        )}
        {view.phase === 'error' && (
          <p className="hint error">{view.message}</p>
        )}
        {view.phase === 'ready' && pieItems.length === 0 && (
          <p className="hint">no data for this selection</p>
        )}
        {view.phase === 'ready' && pieItems.length > 0 && (
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
