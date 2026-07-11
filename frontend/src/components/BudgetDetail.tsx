import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveLine } from '@nivo/line'
import type { Budget } from '../api/budgets'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })
const EUR2 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function subtractDay(iso: string): string {
  const d = new Date(iso)
  d.setDate(d.getDate() - 1)
  return d.toISOString().slice(0, 10)
}

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
  grid: { line: { stroke: '#222228', strokeWidth: 1 } },
  crosshair: { line: { stroke: '#6b6b78', strokeWidth: 1, strokeOpacity: 0.5 } },
  tooltip: { container: { display: 'none' } },
}


export default function BudgetDetail({
  budget,
  onBack,
  onRemoveLink,
  onAssign,
}: {
  budget: Budget
  onBack: () => void
  onRemoveLink: (linkId: number) => void
  onAssign: () => void
}) {
  const { t } = useTranslation()
  const links = useMemo(
    () => [...budget.transactionLinks].sort((a, b) => b.transactionDate.localeCompare(a.transactionDate)),
    [budget.transactionLinks],
  )
  const lineData = useMemo(() => {
    if (budget.chartPoints.length === 0) return []
    return [
      { x: subtractDay(budget.chartPoints[0].date), y: 0 },
      ...budget.chartPoints.map(p => ({ x: p.date, y: p.cumulative })),
    ]
  }, [budget.chartPoints])
  const total = budget.totalContributions

  const hasTarget = budget.targetAmount != null && budget.targetAmount > 0
  const pct = hasTarget ? budget.totalContributions / budget.targetAmount! : null

  const yValues = lineData.map(p => p.y)
  const yMin = Math.min(0, ...yValues)
  const yMax = Math.max(hasTarget ? budget.targetAmount! : 0, ...yValues)
  const yPad = (yMax - yMin) * 0.1 || 100

  const dataMin = Math.min(...yValues)
  const dataMax = Math.max(...yValues)
  const hasGradient = dataMin < 0 && dataMax > 0

  function gradientDefsLayer({ yScale, innerHeight }: { yScale: (v: number) => number; innerHeight: number }) {
    if (!hasGradient) return null
    const zeroY = Math.max(0, Math.min(innerHeight, yScale(0)))
    const pct = `${(zeroY / innerHeight) * 100}%`
    return (
      <defs>
        <linearGradient id="bdg-line-grad" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2={innerHeight}>
          <stop offset={pct} stopColor="#4ade80" />
          <stop offset={pct} stopColor="#f87171" />
        </linearGradient>
      </defs>
    )
  }

  const lineColor = hasGradient ? 'url(#bdg-line-grad)' : total < 0 ? '#f87171' : '#4ade80'

  return (
    <div className="bdt-page">
      <div className="bdt-header">
        <button className="bdt-back-btn" onClick={onBack}>
          ← {t('common.back')}
        </button>

        <div className="bdt-title-block">
          <span className="bdt-budget-name">{budget.name}</span>
          {budget.note && <span className="bdt-budget-note">{budget.note}</span>}
        </div>

        <div className="bdt-meta">
          <div className="bdt-meta-item">
            <span className="bdt-meta-label">{t('budgets.detail.balance')}</span>
            <span className={`bdt-meta-value ${budget.balance >= 0 ? 'positive' : 'negative'}`}>
              {EUR.format(budget.balance)}
            </span>
          </div>
          {hasTarget && (
            <>
              <div className="bdt-meta-sep">·</div>
              <div className="bdt-meta-item">
                <span className="bdt-meta-label">{t('budgets.detail.target')}</span>
                <span className="bdt-meta-value">{EUR.format(budget.targetAmount!)}</span>
              </div>
              <div className="bdt-meta-sep">·</div>
              <div className="bdt-meta-item">
                <div className="bdg-bar bdt-meta-bar">
                  <div className="bdg-bar-track">
                    <div
                      className={`bdg-bar-fill${pct! >= 1 ? ' bdg-bar-fill--done' : ''}`}
                      style={{ width: `${Math.min(pct! * 100, 100)}%` }}
                    />
                  </div>
                  <span className={`bdg-bar-pct${pct! >= 1 ? ' bdg-bar-pct--done' : ''}`}>
                    {Math.round(pct! * 100)}%
                  </span>
                </div>
              </div>
            </>
          )}
        </div>

        <button className="bdg-assign-btn bdt-assign-btn" onClick={onAssign}>
          + {t('budgets.assign')}
        </button>
      </div>

      <div className="bdt-body">
        <div className="bdt-chart-section">
          <span className="bdt-section-label">{t('budgets.detail.cumulativeBalance')}</span>
          {budget.chartPoints.length === 0 ? (
            <p className="hint" style={{ marginTop: '2rem' }}>{t('budgets.noTransactions')}</p>
          ) : (
            <div className="bdt-chart-wrap">
              <ResponsiveLine
                data={[{ id: 'balance', data: lineData }]}
                xScale={{ type: 'time', format: '%Y-%m-%d', precision: 'day', useUTC: false }}
                xFormat="time:%d.%m.%Y"
                yScale={{ type: 'linear', min: yMin - yPad, max: yMax + yPad }}
                curve="stepAfter"
                margin={{ top: 16, right: 24, bottom: 52, left: 90 }}
                axisBottom={{
                  format: '%b %y',
                  tickSize: 0,
                  tickPadding: 10,
                  tickRotation: -30,
                }}
                axisLeft={{
                  tickSize: 0,
                  tickPadding: 10,
                  tickValues: 5,
                  format: v => EUR.format(v as number),
                }}
                layers={['grid', gradientDefsLayer as any, 'axes', 'areas', 'crosshair', 'lines', 'points', 'slices', 'mesh', 'legends'] as any}
                enableGridX={false}
                gridYValues={5}
                lineWidth={2}
                colors={[lineColor]}
                pointSize={7}
                pointColor={hasGradient ? '#6b6b78' : lineColor}
                pointBorderWidth={2}
                pointBorderColor="var(--bg)"
                enableArea
                areaOpacity={0.08}
                enableCrosshair
                useMesh
                markers={
                  hasTarget
                    ? [
                        {
                          axis: 'y' as const,
                          value: budget.targetAmount!,
                          lineStyle: { stroke: '#6b6b78', strokeDasharray: '5 3', strokeWidth: 1 },
                          legend: t('budgets.detail.targetMarker', { amount: EUR.format(budget.targetAmount!) }),
                          legendPosition: 'bottom-right' as const,
                        },
                      ]
                    : []
                }
                tooltip={({ point }) => (
                  <div className="cf-tooltip">
                    <span className="cf-tooltip-period">{point.data.xFormatted as string}</span>
                    <div className="cf-tooltip-row">
                      <span className="cf-dot" style={{ background: '#4ade80' }} />
                      <span>{t('budgets.detail.balance')}</span>
                      <span className="cf-tooltip-amt">{EUR2.format(point.data.y as number)}</span>
                    </div>
                  </div>
                )}
                theme={NIVO_THEME}
              />
            </div>
          )}
        </div>

        <div className="bdt-txns-section">
          <span className="bdt-section-label">{t('budgets.detail.transactions')}</span>
          {links.length === 0 ? (
            <p className="hint" style={{ marginTop: '2rem' }}>{t('budgets.noTransactions')}</p>
          ) : (
            <>
              <div className="bdt-txns-scroll">
                <table className="txn-list-table bdt-txns-table">
                  <thead>
                    <tr>
                      <th>{t('common.date')}</th>
                      <th>{t('common.category')}</th>
                      <th className="txn-col-amount">{t('common.amount')}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {links.map(link => (
                        <tr key={link.id}>
                          <td className="txn-cell-date">{formatDate(link.transactionDate)}</td>
                          <td className="txn-cell-sub bdt-cell-cat">
                            {link.transactionCategory}
                            {link.transactionSubcategory && (
                              <span className="bdt-cell-sub"> / {link.transactionSubcategory}</span>
                            )}
                            {link.transactionPurpose && (
                              <span className="bdt-cell-purpose" title={link.transactionPurpose}>
                                {link.transactionPurpose}
                              </span>
                            )}
                          </td>
                          <td className={`txn-cell-amount${link.effectiveAmount < 0 ? ' negative' : ''}`}>
                            {EUR2.format(link.effectiveAmount)}
                          </td>
                          <td>
                            <button
                              className="bdg-remove-btn"
                              title={t('budgets.remove')}
                              onClick={() => onRemoveLink(link.id)}
                            >
                              ×
                            </button>
                          </td>
                        </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="txn-total-row bdt-total-row">
                <span>{t('common.total')}</span>
                <span className={`txn-total-value${total < 0 ? ' negative' : ''}`}>
                  {EUR2.format(total)}
                </span>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
