import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { ResponsiveBar } from '@nivo/bar'
import { ResponsiveLine } from '@nivo/line'
import { fetchAllTransactions, type TransactionItem } from '../api/transactions'

type RollingWindow = 7 | 14 | 30

interface DayPoint {
  date: string
  label: string
  expenses: number
  rollingAvg: number
  cumulative: number
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })
const EUR0 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
  grid: { line: { stroke: '#222228', strokeWidth: 1 } },
  tooltip: { container: { display: 'none' } },
}

function fillDates(from: string, to: string): string[] {
  const dates: string[] = []
  const d = new Date(from + 'T12:00:00')
  const end = new Date(to + 'T12:00:00')
  while (d <= end) {
    dates.push(d.toISOString().slice(0, 10))
    d.setDate(d.getDate() + 1)
  }
  return dates
}

function shortDate(iso: string): string {
  const [, m, day] = iso.split('-')
  return `${day}.${m}.`
}

function fullDate(iso: string): string {
  const [y, m, day] = iso.split('-')
  return `${day}.${m}.${y}`
}

function addDays(isoFrom: string, days: number): string {
  const d = new Date(isoFrom + 'T12:00:00')
  d.setDate(d.getDate() + Math.floor(days))
  return d.toISOString().slice(0, 10)
}

function buildPoints(transactions: TransactionItem[], from: string, to: string, rollingWindow: RollingWindow): DayPoint[] {
  const byDate = new Map<string, number>()
  for (const tx of transactions) {
    if (tx.amount >= 0) continue
    const effective = Math.abs(Math.min(0, tx.effectiveAmount))
    if (effective === 0) continue
    byDate.set(tx.accountingDate, (byDate.get(tx.accountingDate) ?? 0) + effective)
  }
  const dates = fillDates(from, to)
  const raw = dates.map(date => ({ date, expenses: byDate.get(date) ?? 0 }))
  let cumulative = 0
  return raw.map((d, i) => {
    cumulative += d.expenses
    const windowStart = Math.max(0, i - rollingWindow + 1)
    const slice = raw.slice(windowStart, i + 1)
    const rollingAvg = slice.reduce((s, x) => s + x.expenses, 0) / slice.length
    return { date: d.date, label: shortDate(d.date), expenses: d.expenses, rollingAvg, cumulative }
  })
}

function tickValues(points: DayPoint[]): string[] {
  const step = Math.ceil(points.length / 15)
  return points.filter((_, i) => i % step === 0 || i === points.length - 1).map(p => p.label)
}

function makeRollingAvgLayer(points: DayPoint[], cutoffDateIso: string | null) {
  const avgByLabel = new Map(points.map(p => [p.label, p.rollingAvg]))
  const pastLabels = cutoffDateIso
    ? new Set(points.filter(p => p.date <= cutoffDateIso).map(p => p.label))
    : null
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function RollingAvgLayer({ bars, yScale }: any) {
    if (!bars?.length) return null
    const source = pastLabels ? bars.filter((b: any) => pastLabels.has(b.data.indexValue as string)) : bars
    const pts = [...source]
      .sort((a: any, b: any) => a.x - b.x)
      .map((bar: any) => ({
        cx: bar.x + bar.width / 2,
        cy: yScale(avgByLabel.get(bar.data.indexValue as string) ?? 0),
      }))
    if (pts.length < 2) return null
    return (
      <g>
        <polyline
          points={pts.map(p => `${p.cx},${p.cy}`).join(' ')}
          fill="none"
          stroke="#fb923c"
          strokeWidth={2}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      </g>
    )
  }
}

function makeProjectionBarsLayer(avgPerDay: number, sollPerDay: number | null, futureLabels: Set<string>) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function ProjectionBarsLayer({ bars, yScale }: any) {
    if (!bars?.length) return null
    const futureBars = bars.filter((b: any) => futureLabels.has(b.data.indexValue as string))
    if (!futureBars.length) return null
    const hasSoll = sollPerDay !== null && sollPerDay > 0
    return (
      <g>
        {futureBars.map((bar: any, i: number) => {
          const x = bar.x
          const w = bar.width
          const baseline = yScale(0)
          const istW = hasSoll ? Math.floor((w - 1) / 2) : w
          const sollW = w - istW - (hasSoll ? 1 : 0)
          const istH = Math.max(0, baseline - yScale(avgPerDay))
          const sollH = hasSoll ? Math.max(0, baseline - yScale(sollPerDay!)) : 0
          return (
            <g key={i}>
              <rect x={x} y={yScale(avgPerDay)} width={istW} height={istH} fill="rgba(248,113,113,0.3)" rx={2} />
              {hasSoll && (
                <rect x={x + istW + 1} y={yScale(sollPerDay!)} width={sollW} height={sollH} fill="rgba(96,165,250,0.3)" rx={2} />
              )}
            </g>
          )
        })}
      </g>
    )
  }
}

function makeCumulativeProjectionLayer(
  avgPerDay: number,
  sollPerDay: number | null,
  cumulativeAtToday: number,
  todayLabel: string,
  futurePointLabels: string[],
) {
  let istCum = cumulativeAtToday
  const istPts = [
    { label: todayLabel, y: Math.round(cumulativeAtToday) },
    ...futurePointLabels.map(label => { istCum += avgPerDay; return { label, y: Math.round(istCum) } }),
  ]
  let sollCum = cumulativeAtToday
  const sollPts = sollPerDay !== null ? [
    { label: todayLabel, y: Math.round(cumulativeAtToday) },
    ...futurePointLabels.map(label => { sollCum += sollPerDay; return { label, y: Math.round(sollCum) } }),
  ] : []

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function CumulativeProjectionLayer({ xScale, yScale }: any) {
    const coord = (pts: { label: string; y: number }[]) =>
      pts.map(p => `${xScale(p.label)},${yScale(p.y)}`).join(' ')
    return (
      <g>
        {istPts.length > 1 && (
          <polyline
            points={coord(istPts)}
            fill="none"
            stroke="rgba(248,113,113,0.55)"
            strokeWidth={2}
            strokeDasharray="5 3"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}
        {sollPts.length > 1 && (
          <polyline
            points={coord(sollPts)}
            fill="none"
            stroke="rgba(96,165,250,0.55)"
            strokeWidth={2}
            strokeDasharray="5 3"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}
      </g>
    )
  }
}

export default function BurnRatePage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [points, setPoints] = useState<DayPoint[] | null>(null)
  const [rollingWindow, setRollingWindow] = useState<RollingWindow>(7)

  const [income, setIncome] = useState<number | null>(null)
  const [manualBurnRate, setManualBurnRate] = useState('')

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const resp = await fetchAllTransactions(from, to, iban)
      setPoints(buildPoints(resp.transactions, from, to, rollingWindow))
      setIncome(resp.transactions.filter(tx => tx.amount > 0).reduce((s, tx) => s + Math.max(0, tx.effectiveAmount), 0))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'request failed')
    } finally {
      setLoading(false)
    }
  }

  // Burn rate chart derived values
  const totalExpenses = points ? points.reduce((s, p) => s + p.expenses, 0) : 0
  const todayIso = new Date().toISOString().slice(0, 10)
  const isCurrentPeriod = todayIso >= from && todayIso <= to
  const effectiveTo = isCurrentPeriod ? todayIso : to
  const effectiveDays = points ? Math.max(1, points.filter(p => p.date <= effectiveTo).length) : 1
  const avgPerDay = totalExpenses / effectiveDays

  // Projection values (only when today is within the period)
  const todayPoint = isCurrentPeriod && points ? (points.find(p => p.date === todayIso) ?? null) : null
  const cumulativeAtToday = todayPoint?.cumulative ?? 0
  const todayLabel = todayPoint?.label ?? shortDate(todayIso)
  const futurePoints = isCurrentPeriod && points ? points.filter(p => p.date > todayIso) : []
  const futurePointLabels = futurePoints.map(p => p.label)
  const futureLabels = new Set(futurePointLabels)
  const remainingDays = futurePoints.length
  const sollPerDay = isCurrentPeriod && income !== null && remainingDays > 0 && (income - totalExpenses) > 0
    ? (income - totalExpenses) / remainingDays
    : null

  const barData = points?.map(p => ({ date: p.label, expenses: p.expenses, rollingAvg: p.rollingAvg })) ?? []
  const lineData = points
    ? [{ id: 'cumulative', data: points.map(p => ({ x: p.label, y: Math.round(p.cumulative) })) }]
    : []

  const istProjectedEnd = isCurrentPeriod ? cumulativeAtToday + avgPerDay * remainingDays : 0
  const lineYMax = Math.max(
    points?.[points.length - 1]?.cumulative ?? 0,
    istProjectedEnd,
    income ?? 0,
  )

  const ticks = points ? tickValues(points) : []
  const tickRotation = (points?.length ?? 0) > 20 ? -45 : 0
  const bottomMargin = tickRotation !== 0 ? 72 : 44
  const hasData = points !== null && points.some(p => p.expenses > 0)

  const rollingAvgLayer = points ? makeRollingAvgLayer(points, isCurrentPeriod ? todayIso : null) : null
  const projectionBarsLayer = isCurrentPeriod && futureLabels.size > 0
    ? makeProjectionBarsLayer(avgPerDay, sollPerDay, futureLabels)
    : null
  const cumulativeProjectionLayer = isCurrentPeriod && futurePointLabels.length > 0
    ? makeCumulativeProjectionLayer(avgPerDay, sollPerDay, cumulativeAtToday, todayLabel, futurePointLabels)
    : null

  // Runway derived values
  const parsedManual = manualBurnRate !== '' ? parseFloat(manualBurnRate.replace(',', '.')) : NaN
  const calcRunwayDays = income !== null && avgPerDay > 0 ? income / avgPerDay : null
  const manualRunwayDays = income !== null && !isNaN(parsedManual) && parsedManual > 0 ? income / parsedManual : null
  const maxRunwayDays = Math.max(calcRunwayDays ?? 0, manualRunwayDays ?? 0) || null

  const elapsedDays = (Date.now() - new Date(from + 'T12:00:00').getTime()) / 86_400_000
  const calcBarPct = maxRunwayDays && calcRunwayDays ? (calcRunwayDays / maxRunwayDays) * 100 : 0
  const manualBarPct = maxRunwayDays && manualRunwayDays ? (manualRunwayDays / maxRunwayDays) * 100 : 0
  const todayPct = maxRunwayDays ? Math.min((elapsedDays / maxRunwayDays) * 100, 100) : 0

  const calcEndIso = calcRunwayDays !== null ? addDays(from, calcRunwayDays) : null
  const manualEndIso = manualRunwayDays !== null ? addDays(from, manualRunwayDays) : null
  const trackEndIso = calcRunwayDays !== null && (manualRunwayDays === null || calcRunwayDays >= manualRunwayDays)
    ? calcEndIso : manualEndIso

  const calcDaysRemaining = calcRunwayDays !== null ? Math.ceil(calcRunwayDays - elapsedDays) : null
  const manualDaysRemaining = manualRunwayDays !== null ? Math.ceil(manualRunwayDays - elapsedDays) : null
  const isCalcExhausted = calcDaysRemaining !== null && calcDaysRemaining < 0
  const isManualExhausted = manualDaysRemaining !== null && manualDaysRemaining < 0

  return (
    <div className="cf-page">
      <div className="cf-controls">
        <div className="cf-gran-toggle">
          {([7, 14, 30] as RollingWindow[]).map(w => (
            <button
              key={w}
              className={`cf-gran-btn${rollingWindow === w ? ' active' : ''}`}
              onClick={() => setRollingWindow(w)}
            >
              {t('burnrate.windowBtn', { days: w })}
            </button>
          ))}
        </div>
        <button className="load-btn" onClick={load} disabled={loading}>
          {loading ? '…' : t('common.load')}
        </button>
        {points && (
          <div className="cf-summary">
            <span className="cf-summary-item cf-summary-expenses">
              <span className="cf-summary-label">{t('burnrate.totalLabel')}</span>
              <span className="cf-summary-val">{EUR0.format(totalExpenses)}</span>
            </span>
            <span className="cf-summary-sep">·</span>
            <span className="cf-summary-item">
              <span className="cf-summary-label">{t('burnrate.perDay')}</span>
              <span className="cf-summary-val">{EUR.format(avgPerDay)}</span>
            </span>
            <span className="cf-summary-sep">·</span>
            <span className="cf-summary-item">
              <span className="cf-summary-label">{t('burnrate.perMonth')}</span>
              <span className="cf-summary-val">{EUR0.format(avgPerDay * 30)}</span>
            </span>
          </div>
        )}
      </div>

      <div className="cf-body br-body">
        {error && <p className="hint error">{error}</p>}
        {!error && points === null && !loading && (
          <p className="hint"><Trans i18nKey="common.selectDateAndLoad"><span /><kbd /></Trans></p>
        )}
        {loading && <p className="hint loading">{t('common.fetching')}</p>}
        {points !== null && !hasData && (
          <p className="hint">{t('burnrate.noData')}</p>
        )}

        {points !== null && (
          <div className="br-runway">
            <div className="br-chart-label">{t('burnrate.runwayTitle')}</div>

            <div className="br-runway-controls">
              <span className="range-label">{t('burnrate.burnRateOverride')}</span>
              <input
                className="br-runway-rate-input"
                type="number"
                min="0"
                step="1"
                placeholder={avgPerDay > 0 ? String(Math.round(avgPerDay)) : '—'}
                value={manualBurnRate}
                onChange={e => setManualBurnRate(e.target.value)}
              />
              <span className="range-label">{t('burnrate.perDayUnit')}</span>
            </div>

            {income !== null && calcRunwayDays !== null && (
              <>
                <div className="br-runway-stats">
                  <span className="br-runway-stat">
                    <span className="br-runway-stat-label">{t('burnrate.totalIncome')}</span>
                    <span className="br-runway-stat-val positive">{EUR0.format(income)}</span>
                  </span>
                  <span className="cf-summary-sep">·</span>
                  <span className="br-runway-stat">
                    <span className="br-runway-stat-label">{t('burnrate.calcRate')}</span>
                    <span className="br-runway-stat-val">{EUR.format(avgPerDay)}{t('burnrate.perDayUnit')} → {Math.round(calcRunwayDays)} Tage</span>
                  </span>
                  {manualRunwayDays !== null && (
                    <>
                      <span className="cf-summary-sep">·</span>
                      <span className="br-runway-stat">
                        <span className="br-runway-stat-label">{t('burnrate.manualRate')}</span>
                        <span className="br-runway-stat-val">{EUR.format(parsedManual)}{t('burnrate.perDayUnit')} → {Math.round(manualRunwayDays)} Tage</span>
                      </span>
                    </>
                  )}
                  {sollPerDay !== null && (
                    <>
                      <span className="cf-summary-sep">·</span>
                      <span className="br-runway-stat">
                        <span className="br-runway-stat-label">{t('burnrate.sollRate', { date: fullDate(to) })}</span>
                        <span className="br-runway-stat-val br-runway-stat-val--soll">{EUR.format(sollPerDay)}{t('burnrate.perDayUnit')}</span>
                      </span>
                    </>
                  )}
                </div>

                <div className="br-runway-bars">
                  <div className="br-runway-bar-row">
                    <span className="br-runway-bar-label">{t('burnrate.calcRate')}</span>
                    <div className="br-runway-bar-col">
                      <div className="br-runway-bar-track">
                        <div
                          className={`br-runway-bar-fill${isCalcExhausted ? ' br-runway-bar-fill--exhausted' : ''}`}
                          style={{ width: `${calcBarPct}%` }}
                        />
                        {elapsedDays > 0 && maxRunwayDays !== null && (
                          <div className="br-runway-bar-pin" style={{ left: `${todayPct}%` }} />
                        )}
                      </div>
                      <span className={`br-runway-bar-result${isCalcExhausted ? ' br-runway-bar-result--exhausted' : ''}`}>
                        {isCalcExhausted
                          ? t('burnrate.rowExhausted', { days: Math.abs(calcDaysRemaining!) })
                          : t('burnrate.rowRemaining', { days: calcDaysRemaining, date: fullDate(calcEndIso!) })
                        }
                      </span>
                    </div>
                  </div>

                  {manualRunwayDays !== null && manualDaysRemaining !== null && manualEndIso !== null && (
                    <div className="br-runway-bar-row">
                      <span className="br-runway-bar-label">{t('burnrate.manualRate')}</span>
                      <div className="br-runway-bar-col">
                        <div className="br-runway-bar-track">
                          <div
                            className={`br-runway-bar-fill br-runway-bar-fill--manual${isManualExhausted ? ' br-runway-bar-fill--exhausted' : ''}`}
                            style={{ width: `${manualBarPct}%` }}
                          />
                          {elapsedDays > 0 && maxRunwayDays !== null && (
                            <div className="br-runway-bar-pin" style={{ left: `${todayPct}%` }} />
                          )}
                        </div>
                        <span className={`br-runway-bar-result${isManualExhausted ? ' br-runway-bar-result--exhausted' : ''}`}>
                          {isManualExhausted
                            ? t('burnrate.rowExhausted', { days: Math.abs(manualDaysRemaining) })
                            : t('burnrate.rowRemaining', { days: manualDaysRemaining, date: fullDate(manualEndIso) })
                          }
                        </span>
                      </div>
                    </div>
                  )}

                  <div className="br-runway-bar-row">
                    <div className="br-runway-bar-label-spacer" />
                    <div className="br-runway-bar-dates">
                      <span>{fullDate(from)}</span>
                      {trackEndIso && <span>{fullDate(trackEndIso)}</span>}
                    </div>
                  </div>
                </div>
              </>
            )}
          </div>
        )}

        {hasData && (
          <div className="br-charts">
            <div className="br-chart-block">
              <div className="br-chart-label">
                {t('burnrate.dailyTitle', { days: rollingWindow })}
                {projectionBarsLayer && (
                  <span className="br-chart-legend">
                    <span className="br-legend-dot br-legend-dot--ist" />{t('burnrate.legendIst')}
                    {sollPerDay !== null && <><span className="br-legend-dot br-legend-dot--soll" />{t('burnrate.legendSoll')}</>}
                  </span>
                )}
              </div>
              <div className="br-chart">
                <ResponsiveBar
                  data={barData}
                  keys={['expenses']}
                  indexBy="date"
                  colors={['rgba(248,113,113,0.65)']}
                  borderRadius={2}
                  padding={0.2}
                  margin={{ top: 12, right: 24, bottom: bottomMargin, left: 84 }}
                  axisBottom={{ tickSize: 0, tickPadding: 8, tickRotation, tickValues: ticks }}
                  axisLeft={{ tickSize: 0, tickPadding: 8, tickValues: 4, format: v => EUR0.format(v as number) }}
                  enableLabel={false}
                  enableGridX={false}
                  gridYValues={4}
                  layers={[
                    'grid', 'axes', 'bars',
                    ...(rollingAvgLayer ? [rollingAvgLayer] : []),
                    ...(projectionBarsLayer ? [projectionBarsLayer] : []),
                    'legends',
                  ]}
                  tooltip={({ indexValue, value, data: d }) => (
                    <div className="cf-tooltip">
                      <span className="cf-tooltip-period">{indexValue}</span>
                      <div className="cf-tooltip-row">
                        <span className="cf-dot" style={{ background: '#f87171' }} />
                        <span>{t('burnrate.dailyExpenses')}</span>
                        <span className="cf-tooltip-amt">{EUR.format(value as number)}</span>
                      </div>
                      <div className="cf-tooltip-row">
                        <span className="cf-dot" style={{ background: '#fb923c' }} />
                        <span>{t('burnrate.rollingAvg', { days: rollingWindow })}</span>
                        {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
                        <span className="cf-tooltip-amt">{EUR.format((d as any).rollingAvg)}</span>
                      </div>
                    </div>
                  )}
                  theme={NIVO_THEME}
                />
              </div>
            </div>

            <div className="br-chart-block">
              <div className="br-chart-label">
                {t('burnrate.cumulativeTitle')}
                {cumulativeProjectionLayer && (
                  <span className="br-chart-legend">
                    <span className="br-legend-dot br-legend-dot--ist" />{t('burnrate.legendIst')}
                    {sollPerDay !== null && <><span className="br-legend-dot br-legend-dot--soll" />{t('burnrate.legendSoll')}</>}
                  </span>
                )}
              </div>
              <div className="br-chart">
                <ResponsiveLine
                  data={lineData}
                  margin={{ top: 12, right: 24, bottom: bottomMargin, left: 84 }}
                  xScale={{ type: 'point' }}
                  yScale={{ type: 'linear', min: 0, max: lineYMax > 0 ? lineYMax * 1.05 : 'auto' }}
                  curve="monotoneX"
                  colors={['#f87171']}
                  lineWidth={2}
                  enableArea
                  areaOpacity={0.1}
                  enablePoints={false}
                  useMesh={true}
                  enableCrosshair={true}
                  crosshairType="x"
                  enableGridX={false}
                  gridYValues={4}
                  axisBottom={{ tickSize: 0, tickPadding: 8, tickRotation, tickValues: ticks }}
                  axisLeft={{ tickSize: 0, tickPadding: 8, tickValues: 4, format: v => EUR0.format(v as number) }}
                  layers={[
                    'grid', 'axes', 'areas', 'crosshair', 'lines', 'points', 'slices', 'mesh', 'legends',
                    ...(cumulativeProjectionLayer ? [cumulativeProjectionLayer] : []),
                  ]}
                  tooltip={({ point }) => (
                    <div className="cf-tooltip">
                      <span className="cf-tooltip-period">{String(point.data.x)}</span>
                      <div className="cf-tooltip-row">
                        <span className="cf-dot" style={{ background: '#f87171' }} />
                        <span>{t('burnrate.cumulativeLabel')}</span>
                        <span className="cf-tooltip-amt">{EUR.format(point.data.y as number)}</span>
                      </div>
                    </div>
                  )}
                  theme={NIVO_THEME}
                />
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
