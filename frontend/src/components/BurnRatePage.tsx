import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveBar } from '@nivo/bar'
import { ResponsiveLine } from '@nivo/line'
import DatePicker from 'react-datepicker'
import { de } from 'date-fns/locale'
import 'react-datepicker/dist/react-datepicker.css'
import { fetchBurnRate, type BurnRateResponseDto } from '../api/transactions'

type RollingWindow = 7 | 14 | 30

interface DayPoint {
  date: string
  label: string
  expenses: number
  rollingAvg: number
  cumulative: number
  cumulativeIncome: number
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })
const EUR0 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
  grid: { line: { stroke: '#222228', strokeWidth: 1 } },
  tooltip: { container: { display: 'none' } },
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

function makeSollRateLayer(sollByLabel: Map<string, number>) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function SollRateLayer({ bars, yScale }: any) {
    if (!bars?.length) return null
    const pts = [...bars]
      .filter((b: any) => sollByLabel.has(b.data.indexValue as string))
      .sort((a: any, b: any) => a.x - b.x)
      .map((bar: any) => ({
        cx: bar.x + bar.width / 2,
        cy: yScale(sollByLabel.get(bar.data.indexValue as string) ?? 0),
      }))
    if (pts.length < 2) return null
    return (
      <g>
        <polyline
          points={pts.map(p => `${p.cx},${p.cy}`).join(' ')}
          fill="none"
          stroke="rgba(96,165,250,0.75)"
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

interface ColHoverInfo { clientX: number; clientY: number; label: string }

function makeColumnHoverLayer(onHover: (info: ColHoverInfo | null) => void) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function ColumnHoverLayer({ bars, innerHeight }: any) {
    if (!bars?.length) return null
    const seen = new Set<string>()
    const columns: { label: string; x: number; width: number }[] = []
    for (const bar of bars) {
      const label = bar.data.indexValue as string
      if (!seen.has(label)) {
        seen.add(label)
        columns.push({ label, x: bar.x, width: bar.width })
      }
    }
    return (
      <g>
        {columns.map(({ label, x, width }) => (
          <rect
            key={label}
            x={x}
            y={0}
            width={width}
            height={innerHeight}
            fill="transparent"
            onMouseMove={(e) => onHover({ clientX: e.clientX, clientY: e.clientY, label })}
            onMouseLeave={() => onHover(null)}
          />
        ))}
      </g>
    )
  }
}

function makeCumulativeProjectionLayer(
  istData: { x: string; y: number }[],
  sollData: { x: string; y: number }[],
) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function CumulativeProjectionLayer({ xScale, yScale }: any) {
    const coord = (pts: { x: string; y: number }[]) =>
      pts.map(p => `${xScale(p.x)},${yScale(p.y)}`).join(' ')
    return (
      <g>
        {istData.length > 1 && (
          <polyline
            points={coord(istData)}
            fill="none"
            stroke="rgba(248,113,113,0.55)"
            strokeWidth={2}
            strokeDasharray="5 3"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}
        {sollData.length > 1 && (
          <polyline
            points={coord(sollData)}
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

export default function BurnRatePage({ from, to, accountId }: { from: string; to: string; accountId?: number }) {
  const { t } = useTranslation()

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [burnRateData, setBurnRateData] = useState<BurnRateResponseDto | null>(null)
  const [points, setPoints] = useState<DayPoint[] | null>(null)
  const [rollingWindow, setRollingWindow] = useState<RollingWindow>(7)
  const [simulatedToday, setSimulatedToday] = useState('')
  const [colHover, setColHover] = useState<ColHoverInfo | null>(null)

  const realTodayIso = new Date().toISOString().slice(0, 10)
  const effectiveToday = simulatedToday || realTodayIso

  async function load(overrideWindow?: RollingWindow) {
    setLoading(true)
    setError(null)
    try {
      const resp = await fetchBurnRate(from, to, overrideWindow ?? rollingWindow, accountId)
      setBurnRateData(resp)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'request failed')
    } finally {
      setLoading(false)
    }
  }

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load() }, [from, to, accountId])

  useEffect(() => {
    if (!burnRateData) return
    const filtered = burnRateData.points
      .filter(p => p.date <= effectiveToday)
      .map(p => ({
        date: p.date,
        label: shortDate(p.date),
        expenses: p.expenses,
        rollingAvg: p.rollingAvg,
        cumulative: p.cumulative,
        cumulativeIncome: p.cumulativeIncome,
      }))
    setPoints(filtered)
  }, [burnRateData, effectiveToday])

  // Values from backend
  const totalExpenses = burnRateData?.totalExpenses ?? 0
  const income = burnRateData?.totalIncome ?? 0
  const avgPerDay = burnRateData?.avgPerDay ?? 0

  // Projection values (only when today is within the period)
  const isCurrentPeriod = effectiveToday >= from && effectiveToday <= to
  const effectiveTo = isCurrentPeriod ? effectiveToday : to
  const todayPoint = isCurrentPeriod && points ? (points.find(p => p.date === effectiveToday) ?? null) : null
  const cumulativeAtToday = todayPoint?.cumulative ?? 0
  const todayLabel = todayPoint?.label ?? shortDate(effectiveToday)
  const futurePoints = isCurrentPeriod && points ? points.filter(p => p.date > effectiveToday) : []
  const futurePointLabels = futurePoints.map(p => p.label)
  const futureLabels = new Set(futurePointLabels)
  const remainingDays = futurePoints.length
  const sollPerDay = isCurrentPeriod && burnRateData !== null && remainingDays > 0 && (income - totalExpenses) > 0
    ? (income - totalExpenses) / remainingDays
    : null

  const sollByLabel: Map<string, number> = (() => {
    if (!points || burnRateData === null) return new Map()
    const totalDays = points.length
    return new Map(
      points
        .filter(p => p.date <= effectiveTo)
        .map(p => {
          const idx = points.findIndex(p2 => p2.date === p.date)
          const daysRemaining = totalDays - idx
          return [p.label, daysRemaining > 0 ? Math.max(0, (income - p.cumulative) / daysRemaining) : 0]
        })
    )
  })()

  const barData = points?.map(p => ({ date: p.label, expenses: p.expenses, rollingAvg: p.rollingAvg })) ?? []

  // Projection point arrays (shared between lineData and custom dashed layer)
  const istProjectionData: { x: string; y: number }[] = (() => {
    if (!isCurrentPeriod || futurePointLabels.length === 0) return []
    let c = cumulativeAtToday
    return [
      { x: todayLabel, y: Math.round(cumulativeAtToday) },
      ...futurePointLabels.map(x => { c += avgPerDay; return { x, y: Math.round(c) } }),
    ]
  })()
  const sollProjectionData: { x: string; y: number }[] = (() => {
    if (!isCurrentPeriod || futurePointLabels.length === 0 || sollPerDay === null) return []
    let c = cumulativeAtToday
    return [
      { x: todayLabel, y: Math.round(cumulativeAtToday) },
      ...futurePointLabels.map(x => { c += sollPerDay!; return { x, y: Math.round(c) } }),
    ]
  })()

  const cumulativeIncomeData: { x: string; y: number }[] = points
    ? points.map(p => ({ x: p.label, y: Math.round(p.cumulativeIncome) }))
    : []
  const hasIncome = income > 0

  const netData: { x: string; y: number }[] = hasIncome && points
    ? cumulativeIncomeData.map((inc, i) => ({ x: inc.x, y: inc.y - Math.round(points![i].cumulative) }))
    : []

  const lineYMin = netData.length > 0 ? Math.min(0, ...netData.map(d => d.y)) : 0

  const lineData = points ? [
    { id: 'cumulative', data: points.map(p => ({ x: p.label, y: Math.round(p.cumulative) })) },
    ...(hasIncome ? [{ id: 'income', data: cumulativeIncomeData }] : []),
    ...(netData.length > 0 ? [{ id: 'net', data: netData }] : []),
    ...(istProjectionData.length > 1 ? [{ id: 'ist', data: istProjectionData }] : []),
    ...(sollProjectionData.length > 1 ? [{ id: 'soll', data: sollProjectionData }] : []),
  ] : []

  const istProjectedEnd = isCurrentPeriod ? cumulativeAtToday + avgPerDay * remainingDays : 0
  const lineYMax = Math.max(
    points?.[points.length - 1]?.cumulative ?? 0,
    istProjectedEnd,
    income,
  )

  const ticks = points ? tickValues(points) : []
  const tickRotation = (points?.length ?? 0) > 20 ? -45 : 0
  const bottomMargin = tickRotation !== 0 ? 72 : 44
  const hasData = points !== null && points.some(p => p.expenses > 0)

  const rollingAvgLayer = points ? makeRollingAvgLayer(points, isCurrentPeriod ? effectiveToday : null) : null
  const projectionBarsLayer = isCurrentPeriod && futureLabels.size > 0
    ? makeProjectionBarsLayer(avgPerDay, sollPerDay, futureLabels)
    : null

  const sollRateLayer = sollByLabel.size > 0 ? makeSollRateLayer(sollByLabel) : null
  const columnHoverLayer = makeColumnHoverLayer(setColHover)

  const rollingAvgByLabel = new Map(points?.map(p => [p.label, p.rollingAvg]) ?? [])
  const expensesByLabel = new Map(barData.map(d => [d.date, d.expenses as number]))
  const cumulativeProjectionLayer = istProjectionData.length > 1
    ? makeCumulativeProjectionLayer(istProjectionData, sollProjectionData)
    : null

  // Runway derived values
  const calcRunwayDays = burnRateData !== null && avgPerDay > 0 ? income / avgPerDay : null
  const elapsedDays = (Date.now() - new Date(from + 'T12:00:00').getTime()) / 86_400_000
  const todayPct = calcRunwayDays ? Math.min((elapsedDays / calcRunwayDays) * 100, 100) : 0
  const calcEndIso = calcRunwayDays !== null ? addDays(from, calcRunwayDays) : null
  const calcDaysRemaining = calcRunwayDays !== null ? Math.ceil(calcRunwayDays - elapsedDays) : null
  const isCalcExhausted = calcDaysRemaining !== null && calcDaysRemaining < 0

  return (
    <div className="cf-page">
      <div className="cf-controls">
        <div className="cf-gran-toggle">
          {([7, 14, 30] as RollingWindow[]).map(w => (
            <button
              key={w}
              className={`cf-gran-btn${rollingWindow === w ? ' active' : ''}`}
              onClick={() => { setRollingWindow(w); if (burnRateData !== null) load(w) }}
            >
              {t('burnrate.windowBtn', { days: w })}
            </button>
          ))}
        </div>
        {burnRateData !== null && (
          <div className={`br-sim-field${simulatedToday ? ' br-sim-field--active' : ''}`}>
            <span className="range-label">{t('burnrate.simDate')}</span>
            <DatePicker
              selected={simulatedToday ? new Date(simulatedToday + 'T12:00:00') : null}
              onChange={(date: Date | null) => setSimulatedToday(date ? date.toISOString().slice(0, 10) : '')}
              minDate={new Date(from + 'T12:00:00')}
              maxDate={new Date((to < realTodayIso ? to : realTodayIso) + 'T12:00:00')}
              dateFormat="dd.MM.yyyy"
              locale={de}
              placeholderText="TT.MM.JJJJ"
              className={`br-sim-input${simulatedToday ? ' br-sim-input--active' : ''}`}
              showMonthDropdown
              showYearDropdown
              dropdownMode="select"
              isClearable={false}
            />
            {simulatedToday && (
              <button className="br-sim-clear" onClick={() => setSimulatedToday('')} title={t('burnrate.simClear')}>✕</button>
            )}
          </div>
        )}
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
        {loading && <p className="hint loading">{t('common.fetching')}</p>}
        {points !== null && !hasData && (
          <p className="hint">{t('burnrate.noData')}</p>
        )}

        {points !== null && (
          <div className="br-runway">
            <div className="br-chart-label">{t('burnrate.runwayTitle')}</div>

            {burnRateData !== null && calcRunwayDays !== null && calcEndIso !== null && (
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

                <div className="br-runway-solo">
                  <div className="br-runway-bar-track">
                    <div
                      className={`br-runway-bar-fill${isCalcExhausted ? ' br-runway-bar-fill--exhausted' : ''}`}
                      style={{ width: '100%' }}
                    />
                    {elapsedDays > 0 && (
                      <div className="br-runway-bar-pin" style={{ left: `${todayPct}%` }} />
                    )}
                  </div>
                  <div className="br-runway-bar-dates">
                    <span>{fullDate(from)}</span>
                    <span>{fullDate(calcEndIso)}</span>
                  </div>
                  <span className={`br-runway-bar-result${isCalcExhausted ? ' br-runway-bar-result--exhausted' : ''}`}>
                    {isCalcExhausted
                      ? t('burnrate.rowExhausted', { days: Math.abs(calcDaysRemaining!) })
                      : t('burnrate.rowRemaining', { days: calcDaysRemaining, date: fullDate(calcEndIso) })
                    }
                  </span>
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
                <span className="br-chart-legend">
                  {sollRateLayer && <><span className="br-legend-dot br-legend-dot--soll-rate" />{t('burnrate.legendSollRate')}</>}
                  {projectionBarsLayer && <>
                    <span className="br-legend-dot br-legend-dot--ist" />{t('burnrate.legendIst')}
                    {sollPerDay !== null && <><span className="br-legend-dot br-legend-dot--soll" />{t('burnrate.legendSoll')}</>}
                  </>}
                </span>
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
                    ...(sollRateLayer ? [sollRateLayer] : []),
                    ...(projectionBarsLayer ? [projectionBarsLayer] : []),
                    columnHoverLayer,
                    'legends',
                  ]}
                  // eslint-disable-next-line @typescript-eslint/no-explicit-any
                  tooltip={() => null as any}
                  theme={NIVO_THEME}
                />
                {colHover && (() => {
                  const expenses = expensesByLabel.get(colHover.label) ?? 0
                  const rollingAvg = rollingAvgByLabel.get(colHover.label) ?? 0
                  const sollRate = sollByLabel.get(colHover.label)
                  const leftOffset = colHover.clientX > window.innerWidth * 0.6 ? -180 : 14
                  return (
                    <div
                      className="cf-tooltip"
                      style={{ position: 'fixed', left: colHover.clientX + leftOffset, top: colHover.clientY - 10, pointerEvents: 'none', zIndex: 9999 }}
                    >
                      <span className="cf-tooltip-period">{colHover.label}</span>
                      <div className="cf-tooltip-row">
                        <span className="cf-dot" style={{ background: '#f87171' }} />
                        <span>{t('burnrate.dailyExpenses')}</span>
                        <span className="cf-tooltip-amt">{EUR.format(expenses)}</span>
                      </div>
                      <div className="cf-tooltip-row">
                        <span className="cf-dot" style={{ background: '#fb923c' }} />
                        <span>{t('burnrate.rollingAvg', { days: rollingWindow })}</span>
                        <span className="cf-tooltip-amt">{EUR.format(rollingAvg)}</span>
                      </div>
                      {sollRate !== undefined && (
                        <div className="cf-tooltip-row">
                          <span className="cf-dot" style={{ background: 'rgba(96,165,250,0.85)' }} />
                          <span>{t('burnrate.legendSollRate')}</span>
                          <span className="cf-tooltip-amt">{EUR.format(sollRate)}</span>
                        </div>
                      )}
                    </div>
                  )
                })()}
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
                  yScale={{ type: 'linear', min: lineYMin < 0 ? lineYMin * 1.05 : 0, max: lineYMax > 0 ? lineYMax * 1.05 : 'auto' }}
                  curve="monotoneX"
                  // eslint-disable-next-line @typescript-eslint/no-explicit-any
                  colors={(series: any) => series.id === 'cumulative' ? '#f87171' : series.id === 'income' ? '#4ade80' : series.id === 'net' ? '#fbbf24' : 'rgba(0,0,0,0)'}
                  lineWidth={2}
                  enableArea
                  areaOpacity={0.1}
                  enablePoints={false}
                  enableSlices="x"
                  enableGridX={false}
                  gridYValues={4}
                  axisBottom={{ tickSize: 0, tickPadding: 8, tickRotation, tickValues: ticks }}
                  axisLeft={{ tickSize: 0, tickPadding: 8, tickValues: 4, format: v => EUR0.format(v as number) }}
                  layers={[
                    'grid', 'axes', 'areas', 'lines', 'points', 'slices', 'mesh', 'legends',
                    ...(cumulativeProjectionLayer ? [cumulativeProjectionLayer] : []),
                  ]}
                  sliceTooltip={({ slice }) => (
                    <div className="cf-tooltip">
                      <span className="cf-tooltip-period">{String(slice.points[0].data.x)}</span>
                      {slice.points.map(point => (
                        <div key={point.id} className="cf-tooltip-row">
                          <span className="cf-dot" style={{ background:
                            point.seriesId === 'income' ? '#4ade80' :
                            point.seriesId === 'net'   ? '#fbbf24' :
                            point.seriesId === 'soll' ? 'rgba(96,165,250,0.9)' :
                            point.seriesId === 'ist'  ? 'rgba(248,113,113,0.7)' :
                            '#f87171'
                          }} />
                          <span>{
                            point.seriesId === 'income' ? t('burnrate.cumulativeIncome') :
                            point.seriesId === 'net'   ? t('burnrate.netBalance') :
                            point.seriesId === 'ist'  ? t('burnrate.legendIst') :
                            point.seriesId === 'soll' ? t('burnrate.legendSoll') :
                            t('burnrate.cumulativeLabel')
                          }</span>
                          <span className="cf-tooltip-amt">{EUR.format(point.data.y as number)}</span>
                        </div>
                      ))}
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
