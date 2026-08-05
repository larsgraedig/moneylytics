import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveCalendar } from '@nivo/calendar'
import { fetchCalendarSums } from '../api/calendar'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

type State =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: { day: string; value: number }[]; year: number }

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: 'system-ui, sans-serif' },
  tooltip: { container: { display: 'none' } },
}

export default function KalenderPage({ to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [state, setState] = useState<State>({ status: 'idle' })

  useEffect(() => {
    const year = new Date(to).getFullYear()
    const yearFrom = `${year}-01-01`
    const yearTo = `${year}-12-31`
    setState({ status: 'loading' })
    fetchCalendarSums(yearFrom, yearTo, iban)
      .then(resp => setState({ status: 'ready', data: resp.data.map(d => ({ day: d.day, value: d.value })), year }))
      .catch(e => setState({ status: 'error', message: String(e) }))
  }, [to, iban])

  if (state.status === 'loading') return <div className="cf-page"><p className="cf-hint">{t('common.loading')}</p></div>
  if (state.status === 'error') return <div className="cf-page"><p className="cf-hint">{t('common.requestFailed')}</p></div>
  if (state.status === 'idle') return null
  if (state.data.length === 0) return <div className="cf-page"><p className="cf-hint">{t('kalender.empty')}</p></div>

  const maxVal = Math.max(...state.data.map(d => d.value), 1)
  const year = state.year

  return (
    <div className="cf-page">
      <div style={{ height: 240 }}>
        <ResponsiveCalendar
          data={state.data}
          from={`${year}-01-01`}
          to={`${year}-12-31`}
          emptyColor="var(--border)"
          colors={['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b']}
          margin={{ top: 20, right: 24, bottom: 8, left: 32 }}
          yearSpacing={40}
          monthBorderColor="var(--surface)"
          dayBorderWidth={2}
          dayBorderColor="var(--surface)"
          minValue={0}
          maxValue={Math.ceil(maxVal / 100) * 100}
          theme={NIVO_THEME}
          legends={[
            {
              anchor: 'bottom-right',
              direction: 'row',
              translateY: 36,
              itemCount: 4,
              itemWidth: 42,
              itemHeight: 36,
              itemsSpacing: 14,
              itemDirection: 'right-to-left',
            },
          ]}
          tooltip={({ day, value }) => (
            <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', padding: '6px 10px', borderRadius: 6, fontSize: 12, color: 'var(--text)' }}>
              <span>{new Date(day + 'T12:00:00').toLocaleDateString('de-DE', { day: '2-digit', month: 'short', year: 'numeric' })}</span>
              <br />
              <strong>{EUR.format(Number(value))}</strong>
            </div>
          )}
        />
      </div>
    </div>
  )
}
