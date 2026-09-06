import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { type RecurringOccurrenceItem, type RecurringSeriesItem } from '../api/recurring'

interface Props {
  series: RecurringSeriesItem[]
  days: number
  typeColors: Record<string, string>
  startOffset?: number
}

interface ProjectedOccurrence {
  date: Date
  item: RecurringSeriesItem
  isPast: boolean
  occurrence?: RecurringOccurrenceItem
}

const OCCURRENCE_DEVIATION_COLORS: Record<string, string> = {
  ON_TIME: '#4ade80',
  DATE_SHIFTED: '#60a5fa',
  AMOUNT_CHANGED: '#f59e0b',
}

function occurrenceDeviationDetail(occ: RecurringOccurrenceItem, t: TFunction): string | undefined {
  if (occ.deviation !== 'DATE_SHIFTED' || !occ.expectedDate) return undefined
  const diffDays = Math.round(
    (parseLocalDate(occ.date).getTime() - parseLocalDate(occ.expectedDate).getTime()) / (1000 * 60 * 60 * 24),
  )
  return diffDays > 0
    ? (t('recurring.occurrenceDeviation.daysLate', { count: diffDays }) as string)
    : (t('recurring.occurrenceDeviation.daysEarly', { count: -diffDays }) as string)
}

function parseLocalDate(s: string): Date {
  const [year, month, day] = s.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function addDays(d: Date, n: number): Date {
  const result = new Date(d)
  result.setDate(result.getDate() + n)
  return result
}

function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
}

function gatherOccurrences(
  series: RecurringSeriesItem[],
  startDate: Date,
  endDate: Date,
  today: Date,
): ProjectedOccurrence[] {
  const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const result: ProjectedOccurrence[] = []

  for (const s of series) {
    if (s.isFalsePositive) continue

    const actualDates = new Set<string>()

    for (const occ of s.occurrences) {
      const d = parseLocalDate(occ.date)
      if (d >= startDate && d <= endDate && d < todayMidnight) {
        const key = d.toDateString()
        if (!actualDates.has(key)) {
          actualDates.add(key)
          result.push({ date: new Date(d), item: s, isPast: true, occurrence: occ })
        }
      }
    }

    let d = parseLocalDate(s.nextExpectedDate)
    while (d <= endDate) {
      if (d >= startDate && d >= todayMidnight) {
        result.push({ date: new Date(d), item: s, isPast: false })
      }
      d = addDays(d, s.intervalDays)
    }
  }

  return result.sort((a, b) => a.date.getTime() - b.date.getTime())
}

function groupByDay(occurrences: ProjectedOccurrence[]): Array<{ date: Date; items: ProjectedOccurrence[] }> {
  const groups: Array<{ date: Date; items: ProjectedOccurrence[] }> = []
  for (const occ of occurrences) {
    const last = groups[groups.length - 1]
    if (last && isSameDay(last.date, occ.date)) {
      last.items.push(occ)
    } else {
      groups.push({ date: occ.date, items: [occ] })
    }
  }
  return groups
}

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format(Math.abs(amount))
}

function formatDate(d: Date): string {
  return new Intl.DateTimeFormat('de-DE', { weekday: 'short', day: '2-digit', month: 'short' }).format(d)
}

function formatMonth(d: Date): string {
  return new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' }).format(d)
}

export default function RecurringTimeline({ series, days, typeColors, startOffset = 0 }: Props) {
  const { t } = useTranslation()
  const today = new Date()
  const startDate = addDays(today, startOffset)
  const endDate = addDays(startDate, days)
  const occurrences = gatherOccurrences(series, startDate, endDate, today)
  const groups = groupByDay(occurrences)

  if (groups.length === 0) {
    return <p className="hint">{t('recurring.timelineEmpty')}</p>
  }

  return (
    <div className="rcr-tl-container">
      {groups.map((group, gi) => {
        const isToday = isSameDay(group.date, today)
        const prevGroup = groups[gi - 1]
        const isNewMonth = gi === 0 || (
          group.date.getMonth() !== prevGroup.date.getMonth() ||
          group.date.getFullYear() !== prevGroup.date.getFullYear()
        )
        return (
          <div key={group.date.toISOString()}>
            {isNewMonth && (
              <div className="rcr-tl-month-sep">{formatMonth(group.date)}</div>
            )}
          <div className="rcr-tl-day">
            <div className="rcr-tl-axis">
              <div className={`rcr-tl-dot${isToday ? ' rcr-tl-dot--today' : ''}`} />
              {gi < groups.length - 1 && <div className="rcr-tl-line" />}
            </div>
            <div className="rcr-tl-content">
              <div className={`rcr-tl-date${isToday ? ' rcr-tl-date--today' : ''}`}>
                {isToday ? `${t('recurring.today')} · ${formatDate(group.date)}` : formatDate(group.date)}
              </div>
              <div className="rcr-tl-cards">
                {group.items.map((occ, oi) => (
                  <div
                    key={`${occ.item.fingerprint}-${oi}`}
                    className={`rcr-tl-card rcr-tl-card--${occ.item.direction.toLowerCase()}${occ.isPast ? ' rcr-tl-card--past' : ''}`}
                  >
                    <span
                      className="rcr-tl-card-type"
                      style={{ color: typeColors[occ.item.type] ?? '#6b7280' }}
                    >
                      {t(`recurring.type.${occ.item.type}` as Parameters<typeof t>[0])}
                    </span>
                    <span className="rcr-tl-card-label">{occ.item.label}</span>
                    <span className={`rcr-tl-card-amount rcr-tl-card-amount--${occ.item.direction.toLowerCase()}`}>
                      {occ.item.direction === 'EXPENSE' ? '−' : '+'}
                      {formatAmount(
                        occ.isPast && occ.occurrence ? occ.occurrence.amount : occ.item.expectedAmount,
                        occ.item.currency,
                      )}
                    </span>
                    {occ.item.amountVariable && (
                      <span className="rcr-tl-card-variable" title={t('recurring.amountVariable') as string}>~</span>
                    )}
                    {occ.isPast && occ.occurrence?.deviation && (
                      <span
                        className="rcr-badge rcr-tl-card-deviation"
                        style={{
                          color: OCCURRENCE_DEVIATION_COLORS[occ.occurrence.deviation] ?? '#6b7280',
                          borderColor: OCCURRENCE_DEVIATION_COLORS[occ.occurrence.deviation] ?? '#6b7280',
                        }}
                        title={occurrenceDeviationDetail(occ.occurrence, t)}
                      >
                        {t(`recurring.occurrenceDeviation.${occ.occurrence.deviation}` as Parameters<typeof t>[0])}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
          </div>
        )
      })}
    </div>
  )
}
