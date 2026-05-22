import { useState } from 'react'
import SankeyChart from './components/SankeyChart'
import { fetchSankeyData, type SankeyResponse } from './api/transactions'

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: SankeyResponse }

export default function App() {
  const [from, setFrom] = useState(firstOfYear)
  const [to, setTo] = useState(today)
  const [view, setView] = useState<ViewState>({ phase: 'idle' })

  async function load() {
    setView({ phase: 'loading' })
    try {
      const data = await fetchSankeyData(from, to)
      setView(data.nodes.length === 0 ? { phase: 'idle' } : { phase: 'ready', data })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  return (
    <div className="shell">
      <header className="bar">
        <span className="wordmark">moneylytics</span>

        <div className="controls">
          <fieldset className="range-group">
            <label className="range-field">
              <span className="range-label">from</span>
              <input
                type="date"
                value={from}
                max={to}
                onChange={e => setFrom(e.target.value)}
              />
            </label>
            <div className="range-sep" />
            <label className="range-field">
              <span className="range-label">to</span>
              <input
                type="date"
                value={to}
                min={from}
                max={today}
                onChange={e => setTo(e.target.value)}
              />
            </label>
          </fieldset>

          <button
            className="load-btn"
            onClick={load}
            disabled={view.phase === 'loading'}
          >
            {view.phase === 'loading' ? '…' : 'load'}
          </button>
        </div>
      </header>

      <main className="stage">
        {view.phase === 'idle' && (
          <p className="hint">select a date range and press <kbd>load</kbd></p>
        )}

        {view.phase === 'loading' && (
          <p className="hint loading">fetching…</p>
        )}

        {view.phase === 'error' && (
          <p className="hint error">{view.message}</p>
        )}

        {view.phase === 'ready' && (
          <div className="chart" key={`${from}/${to}`}>
            <SankeyChart data={view.data} />
          </div>
        )}
      </main>
    </div>
  )
}
