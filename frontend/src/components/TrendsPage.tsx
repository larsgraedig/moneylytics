import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveLine } from '@nivo/line'
import type { CategoryNode } from '../api/rawImport'
import type { SankeyNode } from '../api/transactions'
import {
  fetchTrends,
  type Granularity,
  type SeriesConfig,
  type SeriesRole,
  type TrendsResponse,
} from '../api/trends'
import { CategoryPathInput } from './CategoryPathInput'
import TransactionListPanel from './TransactionListPanel'
import { fetchThresholds, type Threshold, type ThresholdPeriod as TThresholdPeriod } from '../api/thresholds'

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
  if (granularity === 'YEARLY') return { from: `${bucket}-01-01`, to: `${bucket}-12-31` }
  if (granularity === 'BI_YEARLY') {
    const [yearStr, hStr] = bucket.split('-H')
    const year = Number(yearStr), half = Number(hStr)
    return half === 1
      ? { from: `${year}-01-01`, to: `${year}-06-30` }
      : { from: `${year}-07-01`, to: `${year}-12-31` }
  }
  if (granularity === 'QUARTERLY') {
    const [yearStr, qStr] = bucket.split('-Q')
    const year = Number(yearStr), q = Number(qStr)
    const startMonth = (q - 1) * 3 + 1
    const endMonth = startMonth + 2
    const lastDay = new Date(year, endMonth, 0).getDate()
    const pad = (n: number) => String(n).padStart(2, '0')
    return { from: `${year}-${pad(startMonth)}-01`, to: `${year}-${pad(endMonth)}-${lastDay}` }
  }
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


let idSeq = 0
function newId() { return String(++idSeq) }

function formatBucket(bucket: string, granularity: Granularity): string {
  if (granularity === 'YEARLY') return bucket
  if (granularity === 'BI_YEARLY') {
    const [y, h] = bucket.split('-')
    return `${h} '${y.slice(2)}`
  }
  if (granularity === 'QUARTERLY') {
    const [y, q] = bucket.split('-')
    return `${q} '${y.slice(2)}`
  }
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


interface ThresholdLine {
  value: number
  color: string
  label: string
  baselineAmount: number
  period: TThresholdPeriod
  granularity: Granularity
}

interface ThresholdTooltipData {
  x: number
  y: number
  line: ThresholdLine
}

type TooltipSetter = (data: ThresholdTooltipData | null) => void

function makeThresholdLayer(lines: ThresholdLine[], setTooltip: TooltipSetter) {
  return function ThresholdLayer({ innerWidth, yScale }: any) {
    if (lines.length === 0) return null
    return (
      <>
        {lines.map((line, i) => {
          const y = (yScale as (v: number) => number)(line.value)
          if (y == null || y < 0) return null
          return (
            <g
              key={i}
              onMouseMove={e => setTooltip({ x: e.clientX, y: e.clientY, line })}
              onMouseLeave={() => setTooltip(null)}
            >
              {/* wider transparent hit area */}
              <line x1={0} y1={y} x2={innerWidth} y2={y} stroke="transparent" strokeWidth={12} />
              <line
                x1={0} y1={y} x2={innerWidth} y2={y}
                stroke={line.color} strokeWidth={1}
                strokeDasharray="6 4" opacity={0.75}
                style={{ pointerEvents: 'none' }}
              />
              <text
                x={innerWidth - 4} y={y - 4}
                fill={line.color} fontSize={9}
                textAnchor="end" fontFamily="ui-monospace,'SF Mono',Consolas,monospace"
                style={{ pointerEvents: 'none' }}
              >
                {line.label}
              </text>
            </g>
          )
        })}
      </>
    )
  }
}

const SEVERITY_COLORS = { notice: '#fbbf24', warning: '#f97316', critical: '#ef4444' }

const DAYS_PER_PERIOD: Record<TThresholdPeriod, number> = {
  WEEKLY: 7,
  MONTHLY: 365.25 / 12,
  QUARTERLY: 365.25 / 4,
  YEARLY: 365.25,
}

const DAYS_PER_BUCKET: Record<Granularity, number> = {
  DAILY: 1,
  WEEKLY: 7,
  MONTHLY: 365.25 / 12,
  QUARTERLY: 365.25 / 4,
  BI_YEARLY: 365.25 / 2,
  YEARLY: 365.25,
}

function normalizeThreshold(amount: number, period: TThresholdPeriod, granularity: Granularity): number {
  return (amount / DAYS_PER_PERIOD[period]) * DAYS_PER_BUCKET[granularity]
}

function findPathById(id: number, nodes: CategoryNode[], prefix: string[] = []): string[] | null {
  for (const node of nodes) {
    const path = [...prefix, node.name]
    if (node.id === id) return path
    const found = findPathById(id, node.children, path)
    if (found) return found
  }
  return null
}

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: TrendsResponse }

export default function TrendsPage({ from, to, accountId, categories }: { from: string; to: string; accountId?: number; categories: CategoryNode[] }) {
  const { t } = useTranslation()
  const [granularity, setGranularity] = useState<Granularity>('MONTHLY')
  const [series, setSeries] = useState<SeriesConfig[]>([{ id: newId(), categoryId: null }])
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [drilldown, setDrilldown] = useState<{ node: SankeyNode; from: string; to: string } | null>(null)
  const [showSubs, setShowSubs] = useState(true)
  const [thresholds, setThresholds] = useState<Threshold[]>([])
  const [hoveredThreshold, setHoveredThreshold] = useState<ThresholdTooltipData | null>(null)

  useEffect(() => {
    fetchThresholds().then(setThresholds).catch(() => {})
  }, [])

  const addSeries = () => setSeries(prev => [...prev, { id: newId(), categoryId: null }])
  const removeSeries = (id: string) => setSeries(prev => prev.filter(s => s.id !== id))
  const setCategoryId = (id: string, categoryId: number | null) => {
    setSeries(prev => prev.map(s => s.id !== id ? s : { ...s, categoryId }))
  }

  const load = async () => {
    const active = series.filter(s => s.categoryId != null)
    if (active.length === 0) return
    setView({ phase: 'loading' })
    try {
      setView({ phase: 'ready', data: await fetchTrends(from, to, active, granularity, accountId) })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { if (series.some(s => s.categoryId != null)) void load() }, [from, to, accountId, series])

  // Flatten groups → nivo line data, all entries in a group share the same color.
  const { lineData, seriesRoles, seriesCategoryIds } = useMemo(() => {
    if (view.phase !== 'ready') return {
      lineData: [],
      seriesRoles: new Map<string, SeriesRole>(),
      seriesCategoryIds: new Map<string, number>(),
    }
    const roles = new Map<string, SeriesRole>()
    const catIds = new Map<string, number>()
    const data = view.data.groups.flatMap((group, i) => {
      const color = COLORS[i % COLORS.length]
      const entries = showSubs ? [group.main, ...group.subs] : [group.main]
      return entries.map(entry => {
        const id = seriesId(i, entry.label ?? '')
        roles.set(id, entry.role)
        if (entry.categoryId != null) catIds.set(id, entry.categoryId)
        return { id, color, role: entry.role, data: view.data.buckets.map((b, j) => ({ x: b, y: entry.data[j] ?? 0 })) }
      })
    })
    return { lineData: data, seriesRoles: roles, seriesCategoryIds: catIds }
  }, [view, showSubs])

  const CustomLineLayer = useMemo(() => makeLineLayer(seriesRoles), [seriesRoles])

  const thresholdLines = useMemo<ThresholdLine[]>(() => {
    if (view.phase !== 'ready') return []
    const gran = view.data.granularity
    const activeCategoryIds = new Set(seriesCategoryIds.values())
    const lines: ThresholdLine[] = []
    for (const t of thresholds) {
      if (!activeCategoryIds.has(t.categoryId)) continue
      const label = t.categoryPath[t.categoryPath.length - 1] ?? ''
      if (t.notice != null)
        lines.push({ value: normalizeThreshold(t.notice, t.period, gran), color: SEVERITY_COLORS.notice, label: `${label} notice`, baselineAmount: t.notice, period: t.period, granularity: gran })
      if (t.warning != null)
        lines.push({ value: normalizeThreshold(t.warning, t.period, gran), color: SEVERITY_COLORS.warning, label: `${label} warning`, baselineAmount: t.warning, period: t.period, granularity: gran })
      if (t.critical != null)
        lines.push({ value: normalizeThreshold(t.critical, t.period, gran), color: SEVERITY_COLORS.critical, label: `${label} critical`, baselineAmount: t.critical, period: t.period, granularity: gran })
    }
    return lines
  }, [view, seriesCategoryIds, thresholds])

  // setHoveredThreshold is a stable useState setter — intentionally omitted from deps
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const ThresholdLayer = useMemo(() => makeThresholdLayer(thresholdLines, setHoveredThreshold), [thresholdLines])

  const formatTick = (bucket: string) =>
    view.phase === 'ready' ? formatBucket(bucket, view.data.granularity) : bucket

  const bucketCount = view.phase === 'ready' ? view.data.buckets.length : 12
  const maxTicks = granularity === 'DAILY' ? 14
    : granularity === 'WEEKLY' ? 12
    : granularity === 'QUARTERLY' ? 8
    : granularity === 'BI_YEARLY' ? 6
    : granularity === 'YEARLY' ? 10
    : 24
  const tickSkip = Math.ceil(bucketCount / maxTicks)
  const tickValues = view.phase === 'ready'
    ? view.data.buckets.filter((_, i) => i % tickSkip === 0)
    : []

  return (
    <div className="tr-page">
      <div className="tr-controls">
        <select
          className="tr-gran-select"
          value={granularity}
          onChange={e => setGranularity(e.target.value as Granularity)}
        >
          <option value="YEARLY">{t('trends.granularity.yearly')}</option>
          <option value="BI_YEARLY">{t('trends.granularity.biYearly')}</option>
          <option value="QUARTERLY">{t('trends.granularity.quarterly')}</option>
          <option value="MONTHLY">{t('trends.granularity.monthly')}</option>
          <option value="WEEKLY">{t('trends.granularity.weekly')}</option>
          <option value="DAILY">{t('trends.granularity.daily')}</option>
        </select>

        <button
          className={`tr-subs-btn${showSubs ? ' active' : ''}`}
          onClick={() => setShowSubs(v => !v)}
        >
          {t('trends.subcategories')}
        </button>

      </div>

      <div className="tr-series-list">
        {series.map((s, i) => (
          <div key={s.id} className="tr-series-row">
            <div className="tr-series-dot" style={{ background: COLORS[i % COLORS.length] }} />
            <CategoryPathInput
              value={s.categoryId}
              onChange={categoryId => setCategoryId(s.id, categoryId)}
              tree={categories}
              allowCreate={false}
              placeholder={t('trends.category')}
              className="tr-series-input tr-series-cat-path"
            />
            {series.length > 1 && (
              <button className="tr-remove-btn" onClick={() => removeSeries(s.id)} title="Remove">×</button>
            )}
          </div>
        ))}
        <button className="tr-add-btn" onClick={addSeries}>{t('trends.addSeries')}</button>
      </div>

      <div className="tr-chart-area" style={view.phase === 'ready' && lineData.length > 0 ? { cursor: 'pointer' } : undefined}>
        {view.phase === 'loading' && <p className="hint loading">{t('common.fetching')}</p>}
        {view.phase === 'error' && <p className="hint error">{view.message}</p>}
        {view.phase === 'ready' && lineData.length === 0 && (
          <p className="hint">{t('trends.noData')}</p>
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
            layers={['grid', 'axes', CustomLineLayer, 'crosshair', 'mesh', ThresholdLayer]}
            onClick={(point: any) => {
              const id = String(point.seriesId)
              const categoryId = seriesCategoryIds.get(id)
              if (categoryId == null) return
              const namePath = findPathById(categoryId, categories) ?? [seriesLabel(id)]
              const { from: bucketFrom, to: bucketTo } = bucketDateRange(String(point.data.x), view.data.granularity)
              setDrilldown({
                node: {
                  name: namePath[namePath.length - 1] ?? '',
                  value: 0,
                  nodeKey: '',
                  categoryId,
                  namePath,
                },
                from: bucketFrom,
                to: bucketTo,
              })
            }}
            tooltip={({ point }) => {
              const label = seriesLabel(String(point.seriesId))
              const role = seriesRoles.get(String(point.seriesId))
              return (
                <div className="tr-tooltip">
                  <span className="tr-tooltip-label" style={{ color: point.seriesColor }}>{label}</span>
                  {role === 'MAIN_CONTEXT' && <span className="tr-tooltip-role">{t('trends.categoryTotal')}</span>}
                  <span className="tr-tooltip-val">{EUR.format(point.data.y as number)}</span>
                  <span className="tr-tooltip-date">{formatBucket(point.data.x as string, view.data.granularity)}</span>
                  <span className="tr-tooltip-hint">{t('trends.clickToDrillDown')}</span>
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

      {hoveredThreshold && (
        <div
          className="tr-threshold-tooltip"
          style={{ left: hoveredThreshold.x + 14, top: hoveredThreshold.y - 10 }}
        >
          <span className="tr-threshold-tooltip-label" style={{ color: hoveredThreshold.line.color }}>
            {hoveredThreshold.line.label}
          </span>
          <div className="tr-threshold-tooltip-row">
            <span className="tr-threshold-tooltip-key">{t('trends.granularityUnit.' + hoveredThreshold.line.granularity)}</span>
            <span className="tr-threshold-tooltip-val">{EUR.format(hoveredThreshold.line.value)}</span>
          </div>
          <div className="tr-threshold-tooltip-row">
            <span className="tr-threshold-tooltip-key">{t('trends.configured')}</span>
            <span className="tr-threshold-tooltip-val">
              {EUR.format(hoveredThreshold.line.baselineAmount)} / {t('trends.periodUnit.' + hoveredThreshold.line.period)}
            </span>
          </div>
        </div>
      )}

      {drilldown && (
        <TransactionListPanel
          node={drilldown.node}
          from={drilldown.from}
          to={drilldown.to}
          onClose={() => setDrilldown(null)}
        />
      )}
    </div>
  )
}
