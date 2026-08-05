import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchThresholdStatus, type ThresholdStatusItem } from '../api/thresholds'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

type State =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; items: ThresholdStatusItem[] }

function ringColor(pct: number): string {
  if (pct > 1) return 'var(--error)'
  if (pct > 0.9) return '#fbbf24'
  return '#4ade80'
}

const R = 52
const SW = 14
const CIRC = 2 * Math.PI * R

export default function BudgetRingePage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [state, setState] = useState<State>({ status: 'idle' })

  useEffect(() => {
    setState({ status: 'loading' })
    fetchThresholdStatus(from, to, iban)
      .then(items => setState({ status: 'ready', items }))
      .catch(e => setState({ status: 'error', message: String(e) }))
  }, [from, to, iban])

  if (state.status === 'loading') return <div className="cf-page"><p className="cf-hint">{t('common.loading')}</p></div>
  if (state.status === 'error') return <div className="cf-page"><p className="cf-hint">{t('common.requestFailed')}</p></div>
  if (state.status === 'idle') return null

  const items = state.items
  if (items.length === 0) return <div className="cf-page"><p className="cf-hint">{t('ringe.empty')}</p></div>

  const limit = (item: ThresholdStatusItem) => item.critical ?? item.warning ?? item.notice ?? 0

  return (
    <div className="cf-page">
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '32px 40px', padding: '24px 0' }}>
        {items.map(item => {
          const cap = limit(item)
          const pct = cap > 0 ? item.spending / cap : 0
          const filled = Math.min(1, pct) * CIRC
          const color = ringColor(pct)
          const catName = item.categoryPath[item.categoryPath.length - 1] ?? '?'
          const cx = R + SW
          const cy = R + SW
          const size = (R + SW) * 2

          return (
            <div key={item.thresholdId} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
              <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
                <circle cx={cx} cy={cy} r={R} fill="none" stroke="var(--border)" strokeWidth={SW} />
                <circle
                  cx={cx} cy={cy} r={R}
                  fill="none"
                  stroke={color}
                  strokeWidth={SW}
                  strokeLinecap="round"
                  strokeDasharray={`${filled} ${CIRC}`}
                  transform={`rotate(-90 ${cx} ${cy})`}
                />
                <text x={cx} y={cy - 4} textAnchor="middle" fill="var(--text)" fontSize={22} fontWeight={720} style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {Math.round(pct * 100)}%
                </text>
                <text x={cx} y={cy + 14} textAnchor="middle" fill="var(--text-muted)" fontSize={11} style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {EUR.format(item.spending)} / {EUR.format(cap)}
                </text>
              </svg>
              <span style={{ fontSize: 14, fontWeight: 650, color: 'var(--text)', textAlign: 'center', maxWidth: size }}>
                {catName}
              </span>
              {pct > 1 && (
                <span style={{ fontSize: 12, color: 'var(--error)', fontWeight: 600 }}>
                  {EUR.format(item.spending - cap)} {t('ringe.overBudget')}
                </span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
