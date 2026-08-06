import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveLine } from '@nivo/line'
import type { Budget } from '../api/budgets'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

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

export default function BudgetDetail({ budget, onBack, onRemoveLink, onAssign }: {
  budget: Budget
  onBack: () => void
  onRemoveLink: (linkId: number) => void
  onAssign: () => void
}) {
  const { t } = useTranslation()
  const links = useMemo(() => [...budget.transactionLinks].sort((a, b) => b.transactionDate.localeCompare(a.transactionDate)), [budget.transactionLinks])
  const lineData = useMemo(() => {
    if (budget.chartPoints.length === 0) return []
    return [{ x: subtractDay(budget.chartPoints[0].date), y: 0 }, ...budget.chartPoints.map(p => ({ x: p.date, y: p.cumulative }))]
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
    const p = `${(zeroY / innerHeight) * 100}%`
    return (
      <defs>
        <linearGradient id="bdg-line-grad" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2={innerHeight}>
          <stop offset={p} stopColor="#4ade80" />
          <stop offset={p} stopColor="#f87171" />
        </linearGradient>
      </defs>
    )
  }

  const lineColor = hasGradient ? 'url(#bdg-line-grad)' : total < 0 ? '#f87171' : '#4ade80'

  return (
    <div className="flex flex-col gap-6 p-6">
      <div className="flex flex-wrap items-start gap-4">
        <Button variant="ghost" size="sm" onClick={onBack}>← {t('common.back')}</Button>
        <div className="flex flex-col gap-0.5">
          <span className="font-medium">{budget.name}</span>
          {budget.note && <span className="text-sm text-muted-foreground">{budget.note}</span>}
        </div>
        <div className="flex flex-wrap items-center gap-4 text-sm ml-auto">
          <div className="flex flex-col gap-0.5">
            <span className="text-xs text-muted-foreground">{t('budgets.detail.balance')}</span>
            <span className={cn('font-medium tabular-nums', budget.balance >= 0 ? 'text-green-500' : 'text-destructive')}>{EUR.format(budget.balance)}</span>
          </div>
          {hasTarget && (
            <>
              <div className="flex flex-col gap-0.5">
                <span className="text-xs text-muted-foreground">{t('budgets.detail.target')}</span>
                <span className="font-medium tabular-nums">{EUR.format(budget.targetAmount!)}</span>
              </div>
              <div className="flex items-center gap-2 min-w-32">
                <div className="relative flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
                  <div className={cn('absolute inset-y-0 left-0 rounded-full', pct! >= 1 ? 'bg-green-500' : 'bg-primary')} style={{ width: `${Math.min(pct! * 100, 100)}%` }} />
                </div>
                <span className="text-xs tabular-nums text-muted-foreground">{Math.round(pct! * 100)}%</span>
              </div>
            </>
          )}
        </div>
        <Button variant="outline" size="sm" onClick={onAssign}>+ {t('budgets.assign')}</Button>
      </div>

      <div className="flex flex-col gap-2">
        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{t('budgets.detail.cumulativeBalance')}</span>
        {budget.chartPoints.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('budgets.noTransactions')}</p>
        ) : (
          <div className="h-48">
            <ResponsiveLine
              data={[{ id: 'balance', data: lineData }]}
              xScale={{ type: 'time', format: '%Y-%m-%d', precision: 'day', useUTC: false }}
              xFormat="time:%d.%m.%Y"
              yScale={{ type: 'linear', min: yMin - yPad, max: yMax + yPad }}
              curve="stepAfter"
              margin={{ top: 16, right: 24, bottom: 52, left: 90 }}
              axisBottom={{ format: '%b %y', tickSize: 0, tickPadding: 10, tickRotation: -30 }}
              axisLeft={{ tickSize: 0, tickPadding: 10, tickValues: 5, format: v => EUR.format(v as number) }}
              layers={['grid', gradientDefsLayer as any, 'axes', 'areas', 'crosshair', 'lines', 'points', 'slices', 'mesh', 'legends'] as any}
              enableGridX={false}
              gridYValues={5}
              lineWidth={2}
              colors={[lineColor]}
              pointSize={7}
              pointColor={hasGradient ? '#6b6b78' : lineColor}
              pointBorderWidth={2}
              pointBorderColor="var(--background)"
              enableArea
              areaOpacity={0.08}
              enableCrosshair
              useMesh
              markers={hasTarget ? [{ axis: 'y' as const, value: budget.targetAmount!, lineStyle: { stroke: '#6b6b78', strokeDasharray: '5 3', strokeWidth: 1 }, legend: t('budgets.detail.targetMarker', { amount: EUR.format(budget.targetAmount!) }), legendPosition: 'bottom-right' as const }] : []}
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

      <div className="flex flex-col gap-2">
        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{t('budgets.detail.transactions')}</span>
        {links.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('budgets.noTransactions')}</p>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('common.date')}</TableHead>
                  <TableHead>{t('common.category')}</TableHead>
                  <TableHead className="text-right">{t('common.amount')}</TableHead>
                  <TableHead className="w-8" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {links.map(link => (
                  <TableRow key={link.id}>
                    <TableCell className="text-xs text-muted-foreground tabular-nums">{formatDate(link.transactionDate)}</TableCell>
                    <TableCell className="text-sm">
                      {link.transactionCategory}
                      {link.transactionSubcategory && <span className="text-muted-foreground"> / {link.transactionSubcategory}</span>}
                      {link.transactionPurpose && <span className="block text-xs text-muted-foreground truncate max-w-56" title={link.transactionPurpose}>{link.transactionPurpose}</span>}
                    </TableCell>
                    <TableCell className={cn('text-right tabular-nums text-sm', link.effectiveAmount < 0 ? 'text-destructive' : '')}>{EUR2.format(link.effectiveAmount)}</TableCell>
                    <TableCell className="p-1">
                      <Button variant="ghost" size="icon-xs" title={t('budgets.remove')} onClick={() => onRemoveLink(link.id)}>×</Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <div className="flex items-center justify-between border-t pt-3">
              <span className="text-sm text-muted-foreground">{t('common.total')}</span>
              <span className={cn('font-medium tabular-nums', total < 0 ? 'text-destructive' : '')}>{EUR2.format(total)}</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
