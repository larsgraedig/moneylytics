import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveRadar } from '@nivo/radar'
import { fetchCategoryTotals } from '../api/transactions'

type State =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: RadarRow[] }

interface RadarRow {
  category: string
  [key: string]: string | number
}

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: 'system-ui, sans-serif' },
  grid: { line: { stroke: '#222228', strokeWidth: 1 } },
}

function shiftMonthBack(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  d.setMonth(d.getMonth() - 1)
  return d.toISOString().slice(0, 10)
}

export default function RadarPage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [state, setState] = useState<State>({ status: 'idle' })

  useEffect(() => {
    setState({ status: 'loading' })
    const prevFrom = shiftMonthBack(from)
    const prevTo = shiftMonthBack(to)
    Promise.all([
      fetchCategoryTotals(from, to, iban),
      fetchCategoryTotals(prevFrom, prevTo, iban),
    ])
      .then(([cur, prev]) => {
        const allCategories = Array.from(
          new Set([...cur.items.map(i => i.name), ...prev.items.map(i => i.name)]),
        )
        const curByName = Object.fromEntries(cur.items.map(i => [i.name, i.value]))
        const prevByName = Object.fromEntries(prev.items.map(i => [i.name, i.value]))
        const maxVal = Math.max(
          ...allCategories.map(c => Math.max(curByName[c] ?? 0, prevByName[c] ?? 0)),
          1,
        )
        const rows: RadarRow[] = allCategories.map(cat => ({
          category: cat,
          [t('radar.current')]: Math.round(((curByName[cat] ?? 0) / maxVal) * 100),
          [t('radar.previous')]: Math.round(((prevByName[cat] ?? 0) / maxVal) * 100),
        }))
        setState({ status: 'ready', data: rows })
      })
      .catch(e => setState({ status: 'error', message: String(e) }))
  }, [from, to, iban])

  if (state.status === 'loading') return <div className="cf-page"><p className="cf-hint">{t('common.loading')}</p></div>
  if (state.status === 'error') return <div className="cf-page"><p className="cf-hint">{t('common.requestFailed')}</p></div>
  if (state.status === 'idle') return null
  if (state.data.length === 0) return <div className="cf-page"><p className="cf-hint">{t('radar.empty')}</p></div>

  const keys = [t('radar.current'), t('radar.previous')]

  return (
    <div className="cf-page">
      <div style={{ height: 460 }}>
        <ResponsiveRadar
          data={state.data}
          keys={keys}
          indexBy="category"
          maxValue={100}
          margin={{ top: 60, right: 120, bottom: 60, left: 120 }}
          curve="linearClosed"
          borderWidth={2}
          borderColor={{ from: 'color' }}
          gridLevels={4}
          gridShape="circular"
          gridLabelOffset={16}
          enableDots={true}
          dotSize={8}
          dotBorderWidth={2}
          enableDotLabel={false}
          fillOpacity={0.15}
          blendMode="normal"
          animate={true}
          colors={['#2a78d6', '#eb6834']}
          theme={NIVO_THEME}
          legends={[
            {
              anchor: 'top-left',
              direction: 'column',
              translateX: -70,
              translateY: -30,
              itemWidth: 80,
              itemHeight: 20,
              itemTextColor: '#6b6b78',
              symbolSize: 12,
              symbolShape: 'circle',
            },
          ]}
        />
      </div>
    </div>
  )
}
