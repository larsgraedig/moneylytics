import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchRecurringSeries, type RecurringSeriesItem } from '../api/recurring'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

type State = { status: 'idle' } | { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; items: RecurringSeriesItem[] }

function dayOfMonth(dateStr: string): number {
  return new Date(dateStr).getDate()
}

export default function AboRadarPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<State>({ status: 'idle' })

  useEffect(() => {
    setState({ status: 'loading' })
    fetchRecurringSeries('EXPENSE')
      .then(items => setState({ status: 'ready', items: items.filter(i => !i.isFalsePositive) }))
      .catch(e => setState({ status: 'error', message: String(e) }))
  }, [])

  if (state.status === 'loading') return <div className="cf-page"><p className="cf-hint">{t('common.loading')}</p></div>
  if (state.status === 'error') return <div className="cf-page"><p className="cf-hint">{t('common.requestFailed')}</p></div>
  if (state.status === 'idle') return null

  const items = state.items
  if (items.length === 0) return <div className="cf-page"><p className="cf-hint">{t('abos.empty')}</p></div>

  const totalPerMonth = items.reduce((sum, i) => sum + Math.abs(i.expectedAmount), 0)

  const W = 720, H = 280
  const padL = 24, padR = 24, axisY = H / 2
  const plotW = W - padL - padR

  function X(day: number) {
    return padL + ((day - 1) / 30) * plotW
  }

  const positioned = items.map((item, idx) => ({
    item,
    x: X(dayOfMonth(item.nextExpectedDate)),
    up: idx % 2 === 0,
  }))

  return (
    <div className="cf-page">
      <div className="cf-controls" style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          <strong>{t('abos.totalPerMonth')}:</strong> {EUR.format(totalPerMonth)}
        </span>
        <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>
          {t('abos.yearTotal', { amount: EUR.format(totalPerMonth * 12) })}
        </span>
      </div>

      <div className="cf-body" style={{ overflowX: 'auto', marginTop: 16 }}>
        <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', minWidth: 480 }}>
          <line x1={padL} y1={axisY} x2={W - padR} y2={axisY} stroke="var(--border-hi)" strokeWidth={2} />
          {[1, 5, 10, 15, 20, 25, 30].map(d => (
            <g key={d}>
              <line x1={X(d)} y1={axisY - 5} x2={X(d)} y2={axisY + 5} stroke="var(--border-hi)" strokeWidth={1.5} />
              <text x={X(d)} y={axisY + 20} textAnchor="middle" fill="var(--text-muted)" fontSize={11} style={{ fontVariantNumeric: 'tabular-nums' }}>{d}.</text>
            </g>
          ))}

          {positioned.map(({ item, x, up }) => {
            const dir = up ? -1 : 1
            const stem = 58
            const by = axisY + dir * stem
            const label = item.label.length > 14 ? item.label.slice(0, 13) + '…' : item.label
            const pw = Math.max(80, label.length * 6.8 + 18)
            const ph = 36
            const px = Math.min(W - padR - pw, Math.max(padL, x - pw / 2))
            const py = up ? by - ph : by

            return (
              <g key={item.fingerprint}>
                <line x1={x} y1={axisY} x2={x} y2={by} stroke="var(--border-hi)" strokeWidth={1.5} />
                <circle cx={x} cy={axisY} r={5} fill="var(--accent)" stroke="var(--bg)" strokeWidth={2} />
                <rect x={px} y={py} width={pw} height={ph} rx={8} fill="var(--surface)" stroke="var(--border)" />
                <text x={px + pw / 2} y={py + 14} textAnchor="middle" fill="var(--text)" fontSize={12} fontWeight={650}>{label}</text>
                <text x={px + pw / 2} y={py + 28} textAnchor="middle" fill="var(--text-muted)" fontSize={11} style={{ fontVariantNumeric: 'tabular-nums' }}>{EUR.format(Math.abs(item.expectedAmount))}</text>
              </g>
            )
          })}
        </svg>
      </div>
    </div>
  )
}
