import { useEffect, useState } from 'react'
import { ResponsiveLine } from '@nivo/line'
import { fetchCamtCategories, type CategoryGroup } from '../api/camtImport'
import { fetchTrends, type Granularity, type SeriesConfig, type TrendsResponse } from '../api/trends'

const COLORS = [
  '#f59e0b',
  '#60a5fa',
  '#4ade80',
  '#f87171',
  '#a78bfa',
  '#34d399',
  '#fb923c',
  '#e879f9',
]

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

let idSeq = 0
function newId() {
  return String(++idSeq)
}

function formatBucket(bucket: string, granularity: Granularity): string {
  if (granularity === 'MONTHLY') {
    const [y, m] = bucket.split('-')
    const d = new Date(Number(y), Number(m) - 1, 1)
    return new Intl.DateTimeFormat('de-DE', { month: 'short', year: '2-digit' }).format(d)
  }
  if (granularity === 'WEEKLY') {
    return bucket.replace(/^\d{4}-/, '')
  }
  const [y, m, d] = bucket.split('-')
  return new Intl.DateTimeFormat('de-DE', { day: 'numeric', month: 'short' }).format(
    new Date(Number(y), Number(m) - 1, Number(d)),
  )
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: TrendsResponse }

export default function TrendsPage() {
  const [from, setFrom] = useState(firstOfYear)
  const [to, setTo] = useState(today)
  const [granularity, setGranularity] = useState<Granularity>('MONTHLY')
  const [series, setSeries] = useState<SeriesConfig[]>([{ id: newId(), category: '', subcategory: '' }])
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [view, setView] = useState<ViewState>({ phase: 'idle' })

  useEffect(() => {
    fetchCamtCategories().then(r => setCategories(r.categories)).catch(() => {})
  }, [])

  const addSeries = () => setSeries(prev => [...prev, { id: newId(), category: '', subcategory: '' }])

  const removeSeries = (id: string) => setSeries(prev => prev.filter(s => s.id !== id))

  const updateSeries = (id: string, field: 'category' | 'subcategory', value: string) => {
    setSeries(prev => prev.map(s =>
      s.id !== id ? s : { ...s, [field]: value, ...(field === 'category' ? { subcategory: '' } : {}) },
    ))
  }

  const load = async () => {
    const activeSeries = series.filter(s => s.category.trim())
    if (activeSeries.length === 0) return
    setView({ phase: 'loading' })
    try {
      const data = await fetchTrends(from, to, activeSeries, granularity)
      setView({ phase: 'ready', data })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  const activeSeries = series.filter(s => s.category.trim())

  const lineData =
    view.phase === 'ready'
      ? view.data.series.map((s, i) => ({
          id: s.label,
          color: COLORS[i % COLORS.length],
          data: view.data.buckets.map((b, j) => ({ x: b, y: s.data[j] ?? 0 })),
        }))
      : []

  const formatTick = (bucket: string) =>
    view.phase === 'ready' ? formatBucket(bucket, view.data.granularity) : bucket

  const tickCount = view.phase === 'ready'
    ? Math.min(view.data.buckets.length, granularity === 'DAILY' ? 14 : granularity === 'WEEKLY' ? 12 : 24)
    : 12

  return (
    <div className="tr-page">
      <div className="tr-controls">
        <fieldset className="range-group">
          <label className="range-field">
            <span className="range-label">from</span>
            <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} />
          </label>
          <div className="range-sep" />
          <label className="range-field">
            <span className="range-label">to</span>
            <input type="date" value={to} min={from} max={today} onChange={e => setTo(e.target.value)} />
          </label>
        </fieldset>

        <select
          className="tr-gran-select"
          value={granularity}
          onChange={e => setGranularity(e.target.value as Granularity)}
        >
          <option value="MONTHLY">monthly</option>
          <option value="WEEKLY">weekly</option>
          <option value="DAILY">daily</option>
        </select>

        <button
          className="load-btn"
          onClick={load}
          disabled={view.phase === 'loading' || activeSeries.length === 0}
        >
          {view.phase === 'loading' ? '…' : 'load'}
        </button>
      </div>

      <div className="tr-series-list">
        {series.map((s, i) => {
          const subcatOptions = categories.find(c => c.name === s.category)?.subcategories ?? []
          return (
            <div key={s.id} className="tr-series-row">
              <div className="tr-series-dot" style={{ background: COLORS[i % COLORS.length] }} />
              <input
                className="tr-series-input"
                list="tr-cat-list"
                placeholder="category"
                value={s.category}
                onChange={e => updateSeries(s.id, 'category', e.target.value)}
              />
              <input
                className="tr-series-input tr-series-subcat"
                list={`tr-sub-list-${s.id}`}
                placeholder="subcategory (optional)"
                value={s.subcategory}
                onChange={e => updateSeries(s.id, 'subcategory', e.target.value)}
              />
              <datalist id={`tr-sub-list-${s.id}`}>
                {subcatOptions.map(sub => <option key={sub} value={sub} />)}
              </datalist>
              {series.length > 1 && (
                <button className="tr-remove-btn" onClick={() => removeSeries(s.id)} title="Remove series">×</button>
              )}
            </div>
          )
        })}
        <button className="tr-add-btn" onClick={addSeries}>+ add series</button>

        <datalist id="tr-cat-list">
          {categories.map(c => <option key={c.name} value={c.name} />)}
        </datalist>
      </div>

      <div className="tr-chart-area">
        {view.phase === 'idle' && (
          <p className="hint">configure series above and press <kbd>load</kbd></p>
        )}
        {view.phase === 'loading' && <p className="hint loading">fetching…</p>}
        {view.phase === 'error' && <p className="hint error">{view.message}</p>}
        {view.phase === 'ready' && lineData.length > 0 && (
          <ResponsiveLine
            data={lineData}
            colors={d => d.color as string}
            margin={{ top: 20, right: 24, bottom: 60, left: 72 }}
            xScale={{ type: 'point' }}
            yScale={{ type: 'linear', min: 0, max: 'auto', stacked: false }}
            axisBottom={{
              tickSize: 4,
              tickPadding: 6,
              tickRotation: -40,
              format: formatTick,
              tickValues: view.data.buckets.filter((_, i) =>
                i % Math.ceil(view.data.buckets.length / tickCount) === 0,
              ),
            }}
            axisLeft={{
              tickSize: 4,
              tickPadding: 6,
              format: (v: number) => EUR.format(v),
            }}
            pointSize={view.data.granularity === 'DAILY' ? 3 : 6}
            pointBorderWidth={2}
            pointBorderColor={{ from: 'seriesColor' }}
            pointColor={{ theme: 'background' }}
            enableGridX={false}
            gridYValues={5}
            useMesh={true}
            legends={lineData.length > 1 ? [{
              anchor: 'bottom',
              direction: 'row',
              justify: false,
              translateX: 0,
              translateY: 56,
              itemsSpacing: 16,
              itemWidth: 140,
              itemHeight: 18,
              itemTextColor: '#9ca3af',
              symbolSize: 10,
              symbolShape: 'circle',
            }] : []}
            tooltip={({ point }) => (
              <div className="tr-tooltip">
                <span className="tr-tooltip-label" style={{ color: point.seriesColor }}>{point.seriesId}</span>
                <span className="tr-tooltip-val">{EUR.format(point.data.y as number)}</span>
                <span className="tr-tooltip-date">{formatBucket(point.data.x as string, view.data.granularity)}</span>
              </div>
            )}
            theme={{
              background: 'transparent',
              text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
              grid: { line: { stroke: '#222228', strokeWidth: 1 } },
              crosshair: { line: { stroke: '#36363f', strokeWidth: 1 } },
              tooltip: { container: { display: 'none' } },
            }}
          />
        )}
        {view.phase === 'ready' && lineData.length === 0 && (
          <p className="hint">no data for the selected series and date range</p>
        )}
      </div>
    </div>
  )
}
