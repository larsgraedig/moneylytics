import { useEffect, useMemo, useState } from 'react'
import { ResponsiveLine } from '@nivo/line'
import { fetchCamtCategories, type CategoryGroup } from '../api/camtImport'
import {
  fetchTrends,
  type Granularity,
  type SeriesConfig,
  type SeriesRole,
  type TrendsResponse,
} from '../api/trends'
import TransactionListPanel from './TransactionListPanel'

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

function bucketDateRange(bucket: string, granularity: Granularity): { from: string; to: string } {
  if (granularity === 'DAILY') return { from: bucket, to: bucket }
  if (granularity === 'MONTHLY') {
    const [y, m] = bucket.split('-').map(Number)
    const lastDay = new Date(y, m, 0).getDate()
    return { from: `${bucket}-01`, to: `${bucket}-${String(lastDay).padStart(2, '0')}` }
  }
  // WEEKLY: "2024-W02" — find the Monday of that ISO week
  const [yearStr, weekStr] = bucket.split('-W')
  const year = Number(yearStr)
  const week = Number(weekStr)
  const jan4 = new Date(year, 0, 4)
  const monday = new Date(jan4)
  monday.setDate(jan4.getDate() - ((jan4.getDay() + 6) % 7) + (week - 1) * 7)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  return { from: isoDate(monday), to: isoDate(sunday) }
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

let idSeq = 0
function newId() { return String(++idSeq) }

function formatBucket(bucket: string, granularity: Granularity): string {
  if (granularity === 'MONTHLY') {
    const [y, m] = bucket.split('-')
    return new Intl.DateTimeFormat('de-DE', { month: 'short', year: '2-digit' }).format(
      new Date(Number(y), Number(m) - 1, 1),
    )
  }
  if (granularity === 'WEEKLY') return bucket.replace(/^\d{4}-/, '')
  const [y, m, d] = bucket.split('-')
  return new Intl.DateTimeFormat('de-DE', { day: 'numeric', month: 'short' }).format(
    new Date(Number(y), Number(m) - 1, Number(d)),
  )
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

// Unique ID for a series entry within a group.
function seriesId(groupIdx: number, label: string) { return `${groupIdx}\x00${label}` }
function seriesLabel(id: string) { return id.split('\x00').slice(1).join('\x00') }

// Line style per role.
function lineWidth(role: SeriesRole) {
  return role === 'MAIN_SELECTED' || role === 'SUB_SELECTED' ? 3 : 1.5
}
function strokeDash(role: SeriesRole) {
  return role === 'MAIN_CONTEXT' ? '7 4' : undefined
}
function lineOpacity(role: SeriesRole) {
  if (role === 'MAIN_SELECTED' || role === 'SUB_SELECTED') return 1
  if (role === 'MAIN_CONTEXT') return 0.55
  return 0.38  // SUB_CONTEXT
}

// Custom lines layer — replaces nivo's built-in 'lines' to allow per-series stroke width.
function makeLineLayer(roles: Map<string, SeriesRole>) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function LineLayer({ series, lineGenerator }: any) {
    // Draw context lines first, then primary lines on top.
    const sorted = [...series].sort((a: any, b: any) => {
      const ra = roles.get(a.id) ?? 'SUB_CONTEXT'
      const rb = roles.get(b.id) ?? 'SUB_CONTEXT'
      const order = { SUB_CONTEXT: 0, MAIN_CONTEXT: 1, MAIN_SELECTED: 2, SUB_SELECTED: 2 }
      return order[ra] - order[rb]
    })

    return (
      <>
        {sorted.map((serie: any) => {
          const role = roles.get(serie.id) ?? 'SUB_CONTEXT'
          return (
            <path
              key={serie.id}
              d={lineGenerator(serie.data.map((d: any) => d.position)) ?? ''}
              fill="none"
              stroke={serie.color}
              strokeWidth={lineWidth(role)}
              strokeDasharray={strokeDash(role)}
              opacity={lineOpacity(role)}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )
        })}
      </>
    )
  }
}

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
  const [drilldown, setDrilldown] = useState<{ nodeKey: string; from: string; to: string } | null>(null)

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
    const active = series.filter(s => s.category.trim())
    if (active.length === 0) return
    setView({ phase: 'loading' })
    try {
      setView({ phase: 'ready', data: await fetchTrends(from, to, active, granularity) })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  // Flatten groups → nivo line data, all entries in a group share the same color.
  const { lineData, seriesRoles } = useMemo(() => {
    if (view.phase !== 'ready') return { lineData: [], seriesRoles: new Map<string, SeriesRole>() }
    const roles = new Map<string, SeriesRole>()
    const data = view.data.groups.flatMap((group, i) => {
      const color = COLORS[i % COLORS.length]
      const entries = [group.main, ...group.subs]
      return entries.map(entry => {
        const id = seriesId(i, entry.label)
        roles.set(id, entry.role)
        return { id, color, role: entry.role, data: view.data.buckets.map((b, j) => ({ x: b, y: entry.data[j] ?? 0 })) }
      })
    })
    return { lineData: data, seriesRoles: roles }
  }, [view])

  const CustomLineLayer = useMemo(() => makeLineLayer(seriesRoles), [seriesRoles])

  const formatTick = (bucket: string) =>
    view.phase === 'ready' ? formatBucket(bucket, view.data.granularity) : bucket

  const bucketCount = view.phase === 'ready' ? view.data.buckets.length : 12
  const maxTicks = granularity === 'DAILY' ? 14 : granularity === 'WEEKLY' ? 12 : 24
  const tickSkip = Math.ceil(bucketCount / maxTicks)
  const tickValues = view.phase === 'ready'
    ? view.data.buckets.filter((_, i) => i % tickSkip === 0)
    : []

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
          disabled={view.phase === 'loading' || series.every(s => !s.category.trim())}
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
                <button className="tr-remove-btn" onClick={() => removeSeries(s.id)} title="Remove">×</button>
              )}
            </div>
          )
        })}
        <button className="tr-add-btn" onClick={addSeries}>+ add series</button>

        <datalist id="tr-cat-list">
          {categories.map(c => <option key={c.name} value={c.name} />)}
        </datalist>
      </div>

      <div className="tr-chart-area" style={view.phase === 'ready' && lineData.length > 0 ? { cursor: 'pointer' } : undefined}>
        {view.phase === 'idle' && <p className="hint">configure series above and press <kbd>load</kbd></p>}
        {view.phase === 'loading' && <p className="hint loading">fetching…</p>}
        {view.phase === 'error' && <p className="hint error">{view.message}</p>}
        {view.phase === 'ready' && lineData.length === 0 && (
          <p className="hint">no data for the selected series and date range</p>
        )}
        {view.phase === 'ready' && lineData.length > 0 && (
          <ResponsiveLine
            data={lineData}
            colors={d => (d as { color: string }).color}
            margin={{ top: 20, right: 24, bottom: 60, left: 72 }}
            xScale={{ type: 'point' }}
            yScale={{ type: 'linear', min: 0, max: 'auto', stacked: false }}
            axisBottom={{
              tickSize: 4,
              tickPadding: 6,
              tickRotation: -40,
              format: formatTick,
              tickValues,
            }}
            axisLeft={{
              tickSize: 4,
              tickPadding: 6,
              format: (v: number) => EUR.format(v),
            }}
            lineWidth={0}
            pointSize={0}
            enableGridX={false}
            gridYValues={5}
            useMesh={true}
            layers={['grid', 'axes', CustomLineLayer, 'crosshair', 'mesh']}
            onClick={(point: any) => {
              const id = String(point.seriesId)
              const nullIdx = id.indexOf('\x00')
              const groupIdx = Number(id.slice(0, nullIdx))
              const label = id.slice(nullIdx + 1)
              const role = seriesRoles.get(id)
              const seriesConfig = series[groupIdx]
              if (!seriesConfig) return
              const category = seriesConfig.category
              const subcategory = (role === 'SUB_SELECTED' || role === 'SUB_CONTEXT') ? label : undefined
              const nodeKey = subcategory ? `sub:${category}:${subcategory}` : `cat:${category}`
              const { from: bucketFrom, to: bucketTo } = bucketDateRange(String(point.data.x), view.data.granularity)
              setDrilldown({ nodeKey, from: bucketFrom, to: bucketTo })
            }}
            tooltip={({ point }) => {
              const label = seriesLabel(String(point.seriesId))
              const role = seriesRoles.get(String(point.seriesId))
              return (
                <div className="tr-tooltip">
                  <span className="tr-tooltip-label" style={{ color: point.seriesColor }}>{label}</span>
                  {role === 'MAIN_CONTEXT' && <span className="tr-tooltip-role">category total</span>}
                  <span className="tr-tooltip-val">{EUR.format(point.data.y as number)}</span>
                  <span className="tr-tooltip-date">{formatBucket(point.data.x as string, view.data.granularity)}</span>
                  <span className="tr-tooltip-hint">· click to drill down</span>
                </div>
              )
            }}
            theme={{
              background: 'transparent',
              text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
              grid: { line: { stroke: '#222228', strokeWidth: 1 } },
              crosshair: { line: { stroke: '#36363f', strokeWidth: 1 } },
              tooltip: { container: { display: 'none' } },
            }}
          />
        )}
      </div>

      {drilldown && (
        <TransactionListPanel
          nodeKey={drilldown.nodeKey}
          from={drilldown.from}
          to={drilldown.to}
          onClose={() => setDrilldown(null)}
        />
      )}
    </div>
  )
}
