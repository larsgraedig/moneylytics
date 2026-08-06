import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsivePie } from '@nivo/pie'
import { fetchCategoryTotals, type CategoryTotalItem } from '../api/transactions'
import type { SankeyNode } from '../api/transactions'
import TransactionListPanel from './TransactionListPanel'
import { Button } from '@/components/ui/button'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

const LABEL_RING_OFFSET = 120
const LABEL_GAP_PX = 60

function makeLabelsLayer(handlerRef: React.MutableRefObject<(datum: any) => void>) {
  return function PieLabelsLayer({ dataWithArc, centerX, centerY, radius }: any) {
    const labelRadius = radius + LABEL_RING_OFFSET
    const items: Array<{ datum: any; origAngle: number; angle: number }> =
      dataWithArc.map((d: any) => {
        const midAngle = (d.arc.startAngle + d.arc.endAngle) / 2 - Math.PI / 2
        return { datum: d, origAngle: midAngle, angle: midAngle }
      })
    items.sort((a, b) => a.angle - b.angle)
    const minGap = LABEL_GAP_PX / labelRadius
    for (let i = 1; i < items.length; i++) {
      if (items[i].angle - items[i - 1].angle < minGap) items[i].angle = items[i - 1].angle + minGap
    }
    for (let i = items.length - 2; i >= 0; i--) {
      if (items[i + 1].angle - items[i].angle < minGap) items[i].angle = items[i + 1].angle - minGap
    }
    return (
      <g transform={`translate(${centerX}, ${centerY})`}>
        {items.map(({ datum, origAngle, angle }) => {
          const outerR = datum.arc.outerRadius as number
          const p0x = Math.cos(origAngle) * outerR
          const p0y = Math.sin(origAngle) * outerR
          const elbowR = outerR + 12
          const ex = Math.cos(origAngle) * elbowR
          const ey = Math.sin(origAngle) * elbowR
          const lx = Math.cos(angle) * labelRadius
          const ly = Math.sin(angle) * labelRadius
          const cp1x = Math.cos(origAngle) * (outerR + 40)
          const cp1y = Math.sin(origAngle) * (outerR + 40)
          const cp2x = Math.cos(angle) * (labelRadius * 0.82)
          const cp2y = Math.sin(angle) * (labelRadius * 0.82)
          const cos = Math.cos(angle)
          const textAnchor = cos > 0.15 ? 'start' : cos < -0.15 ? 'end' : 'middle'
          const textOffset = cos > 0.15 ? 4 : cos < -0.15 ? -4 : 0
          return (
            <g key={String(datum.id)} onClick={() => handlerRef.current(datum)} style={{ cursor: 'pointer' }}>
              <path d={`M ${p0x},${p0y} L ${ex},${ey} C ${cp1x},${cp1y} ${cp2x},${cp2y} ${lx},${ly}`} fill="none" stroke="transparent" strokeWidth={10} />
              <path d={`M ${p0x},${p0y} L ${ex},${ey} C ${cp1x},${cp1y} ${cp2x},${cp2y} ${lx},${ly}`} fill="none" stroke={datum.color} strokeWidth={1} opacity={0.5} />
              <text x={lx + textOffset} y={ly} textAnchor={textAnchor} dominantBaseline="central" fill="#6b6b78" fontSize={11} fontFamily="ui-monospace, 'SF Mono', Consolas, monospace">
                {String(datum.label)}
              </text>
            </g>
          )
        })}
      </g>
    )
  }
}

interface NavEntry { categoryId: number; name: string }
interface PieItem { id: string; label: string; value: number; item: CategoryTotalItem }
type PieDataState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; items: CategoryTotalItem[] }

export default function PiePage({ from, to, accountId }: { from: string; to: string; accountId?: number }) {
  const { t } = useTranslation()
  const [pieData, setPieData] = useState<PieDataState>({ phase: 'idle' })
  const [navStack, setNavStack] = useState<NavEntry[]>([])
  const [drilldown, setDrilldown] = useState<{ node: SankeyNode } | null>(null)
  const handleClickRef = useRef<(datum: any) => void>(() => {})
  const labelsLayer = useMemo(() => makeLabelsLayer(handleClickRef), [])

  async function loadLevel(categoryId?: number) {
    setPieData({ phase: 'loading' })
    setDrilldown(null)
    try {
      const data = await fetchCategoryTotals(from, to, accountId, undefined, categoryId)
      setPieData(data.items.length === 0 ? { phase: 'idle' } : { phase: 'ready', items: data.items })
    } catch (e) {
      setPieData({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  async function load() {
    setNavStack([])
    await loadLevel()
  }

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load() }, [from, to, accountId])

  function navigateTo(stackIndex: number) {
    const newStack = navStack.slice(0, stackIndex + 1)
    setNavStack(newStack)
    loadLevel(newStack[newStack.length - 1]?.categoryId)
  }

  function navigateToRoot() {
    setNavStack([])
    loadLevel()
  }

  function makeNode(categoryId: number, namePath: string[]): SankeyNode {
    return { name: namePath[namePath.length - 1] ?? '', value: 0, nodeKey: '', categoryId, namePath }
  }

  handleClickRef.current = handleSliceClick

  async function handleSliceClick(datum: any) {
    const item = datum.data.item as CategoryTotalItem
    if (item.categoryId == null) {
      const nodeKey = navStack.length === 0 ? `cat:${item.name}` : `sub:${navStack[0]?.name}:${item.name}`
      setDrilldown({ node: { name: item.name, value: 0, nodeKey, categoryId: -1, namePath: [item.name] } })
      return
    }
    const newStack = [...navStack, { categoryId: item.categoryId, name: item.name }]
    const namePath = newStack.map(e => e.name)
    setPieData({ phase: 'loading' })
    setDrilldown(null)
    try {
      const data = await fetchCategoryTotals(from, to, accountId, undefined, item.categoryId)
      if (data.items.length === 0) {
        setPieData(pieData)
        setDrilldown({ node: makeNode(item.categoryId, namePath) })
      } else {
        setNavStack(newStack)
        setPieData({ phase: 'ready', items: data.items })
      }
    } catch (e) {
      setPieData({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  const pieItems: PieItem[] = pieData.phase === 'ready'
    ? pieData.items.map(item => ({ id: item.name, label: item.name, value: item.value, item }))
    : []

  const currentCategoryId = navStack[navStack.length - 1]?.categoryId
  const currentNamePath = navStack.map(e => e.name)

  return (
    <div className="flex flex-col h-full">
      {navStack.length > 0 && (
        <div className="flex items-center gap-1.5 px-4 py-2 border-b text-sm flex-wrap">
          <Button variant="ghost" size="sm" onClick={navigateToRoot}>{t('breakdown.backToCategories')}</Button>
          {navStack.map((entry, idx) => (
            <span key={idx} className="flex items-center gap-1.5">
              <span className="text-muted-foreground">/</span>
              {idx < navStack.length - 1 ? (
                <Button variant="ghost" size="sm" onClick={() => navigateTo(idx)}>{entry.name}</Button>
              ) : (
                <span className="font-medium">{entry.name}</span>
              )}
            </span>
          ))}
          {currentCategoryId != null && (
            <Button variant="outline" size="sm" className="ml-2" onClick={() => setDrilldown({ node: makeNode(currentCategoryId, currentNamePath) })}>
              {t('breakdown.allTransactions')}
            </Button>
          )}
        </div>
      )}

      <div className="flex-1 relative">
        {pieData.phase === 'loading' && <p className="hint loading">{t('common.fetching')}</p>}
        {pieData.phase === 'error' && <p className="hint error">{pieData.message}</p>}
        {pieData.phase === 'ready' && pieItems.length === 0 && <p className="hint">{t('breakdown.noData')}</p>}
        {pieData.phase === 'ready' && pieItems.length > 0 && (
          <ResponsivePie
            data={pieItems}
            innerRadius={0.55}
            padAngle={0.5}
            cornerRadius={3}
            colors={{ scheme: 'tableau10' }}
            margin={{ top: 130, right: 280, bottom: 130, left: 280 }}
            enableArcLabels={false}
            enableArcLinkLabels={false}
            layers={['arcs', 'arcLabels', labelsLayer, 'legends']}
            onClick={handleSliceClick}
            activeOuterRadiusOffset={6}
            tooltip={({ datum }) => (
              <div className="pi-tooltip">
                <span style={{ color: datum.color }}>{datum.label}</span>
                <span>{EUR.format(datum.value)}</span>
              </div>
            )}
            theme={{ background: 'transparent', text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" } }}
          />
        )}
      </div>

      {drilldown && (
        <TransactionListPanel
          node={drilldown.node}
          from={from}
          to={to}
          accountId={accountId}
          onClose={() => setDrilldown(null)}
        />
      )}
    </div>
  )
}
