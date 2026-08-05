import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveBar } from '@nivo/bar'
import { fetchCategoryTotals, fetchCashflow } from '../api/transactions'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

type State =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; bars: WaterfallBar[] }

interface WaterfallBar {
  [key: string]: string | number
  label: string
  offset: number
  value: number
  total: number
  color: string
}

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: 'system-ui, sans-serif' },
  grid: { line: { stroke: '#222228', strokeWidth: 1 } },
  tooltip: { container: { display: 'none' } },
}

function buildWaterfall(income: number, categories: { name: string; value: number }[]): WaterfallBar[] {
  const bars: WaterfallBar[] = []
  let running = income

  bars.push({ label: 'Einnahmen', offset: 0, value: income, total: income, color: '#4ade80' })

  const sorted = [...categories].sort((a, b) => b.value - a.value).slice(0, 12)
  for (const cat of sorted) {
    const before = running
    running -= cat.value
    bars.push({
      label: cat.name.length > 14 ? cat.name.slice(0, 13) + '…' : cat.name,
      offset: Math.max(0, running),
      value: cat.value,
      total: before,
      color: '#eb6834',
    })
  }

  bars.push({ label: 'Endsaldo', offset: 0, value: Math.max(0, running), total: Math.max(0, running), color: '#2a78d6' })
  return bars
}

export default function WasserfallPage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [state, setState] = useState<State>({ status: 'idle' })

  useEffect(() => {
    setState({ status: 'loading' })
    Promise.all([fetchCashflow(from, to, 'monthly', iban), fetchCategoryTotals(from, to, iban)])
      .then(([cf, cats]) => {
        const totalIncome = cf.buckets.reduce((s, b) => s + b.incomeNet, 0)
        const bars = buildWaterfall(
          Math.round(totalIncome),
          cats.items.map(i => ({ name: i.name, value: Math.round(i.value) })),
        )
        setState({ status: 'ready', bars })
      })
      .catch(e => setState({ status: 'error', message: String(e) }))
  }, [from, to, iban])

  if (state.status === 'loading') return <div className="cf-page"><p className="cf-hint">{t('common.loading')}</p></div>
  if (state.status === 'error') return <div className="cf-page"><p className="cf-hint">{t('common.requestFailed')}</p></div>
  if (state.status === 'idle') return null
  if (state.bars.length === 0) return <div className="cf-page"><p className="cf-hint">{t('common.noData')}</p></div>

  const bars = state.bars

  return (
    <div className="cf-page">
      <div style={{ height: 420 }}>
        <ResponsiveBar
          data={bars}
          keys={['offset', 'value']}
          indexBy="label"
          margin={{ top: 20, right: 24, bottom: 60, left: 72 }}
          padding={0.25}
          valueScale={{ type: 'linear' }}
          colors={({ id, data }) => id === 'offset' ? 'transparent' : (data as WaterfallBar).color}
          borderRadius={4}
          borderColor="transparent"
          axisLeft={{
            tickSize: 0,
            tickPadding: 8,
            format: v => `${Math.round((v as number) / 1000)}k`,
          }}
          axisBottom={{
            tickSize: 0,
            tickPadding: 8,
          }}
          enableLabel={true}
          label={d => d.id === 'offset' ? '' : EUR.format(d.value as number)}
          labelTextColor={{ from: 'color', modifiers: [['brighter', 3]] }}
          labelSkipWidth={60}
          labelSkipHeight={16}
          enableGridY={true}
          isInteractive={true}
          theme={NIVO_THEME}
          tooltip={({ data }) => (
            <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: 6, fontSize: 13, color: 'var(--text)' }}>
              <strong>{(data as WaterfallBar).label}</strong><br />
              {EUR.format((data as WaterfallBar).value)}
            </div>
          )}
        />
      </div>
    </div>
  )
}
