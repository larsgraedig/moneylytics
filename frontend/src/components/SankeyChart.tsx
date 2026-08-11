import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveSankey } from '@nivo/sankey'
import type { DefaultLink, DefaultNode, SankeyLabelComponent, SankeyNodeDatum } from '@nivo/sankey'
import { Text } from '@nivo/text'
import { useTooltip } from '@nivo/tooltip'
import { Minus, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { SankeyResponse } from '../api/transactions'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

import type { SankeyNode as SankeyNodeData } from '../api/transactions'

interface Props {
  data: SankeyResponse
  onNodeClick?: (node: SankeyNodeData) => void
}

const nivoTheme = {
  tooltip: {
    container: {
      background: '#16161a',
      color: '#e2e2e8',
      fontSize: 12,
      fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace",
      border: '1px solid #2a2a32',
      borderRadius: 3,
      boxShadow: '0 4px 16px rgba(0,0,0,0.6)',
    } as React.CSSProperties,
  },
  labels: {
    text: {
      fontSize: 11,
      fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace",
    },
  },
} as const

const NODE_SPACING = 8
// Max pixel height for the single tallest node. Keeping it below ~120 px lets
// d3-sankey's redistribution iterations resolve collisions without the dominant
// node pushing neighbours out of bounds.
const MAX_NODE_PX = 120
const MARGINS = 64 // top + bottom margin

function NodeTooltipContent({ node }: { node: SankeyNodeDatum<DefaultNode, DefaultLink> }) {
  const { t } = useTranslation()
  return (
    <div style={{
      background: '#16161a',
      border: '1px solid #2a2a32',
      borderRadius: 3,
      padding: '7px 11px',
      boxShadow: '0 4px 16px rgba(0,0,0,0.6)',
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      color: '#e2e2e8',
      fontSize: 12,
      fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace",
    }}>
      <span style={{
        width: 10,
        height: 10,
        borderRadius: 2,
        background: node.color,
        flexShrink: 0,
      }} />
      <span style={{ fontWeight: 600 }}>{node.label || '—'}</span>
      <span style={{ color: '#6b6b78' }}>{t('common.total')}</span>
      <span>{node.formattedValue}</span>
    </div>
  )
}

export default function SankeyChart({ data, onNodeClick }: Props) {
  const { t } = useTranslation()
  const [maxDepth, setMaxDepth] = useState(1)

  const targetSet = new Set(data.links.map(l => l.target))
  const sourceSet = new Set(data.links.map(l => l.source))

  const childrenOf = new Map<number, { child: number; linkValue: number }[]>()
  data.links.forEach(link => {
    if (!childrenOf.has(link.source)) childrenOf.set(link.source, [])
    childrenOf.get(link.source)!.push({ child: link.target, linkValue: link.value })
  })

  const visited = new Set<number>()
  const orderedNodes: number[] = []
  const nodeDepth = new Map<number, number>()

  const dfs = (nodeIdx: number, depth: number) => {
    if (visited.has(nodeIdx)) return
    visited.add(nodeIdx)
    nodeDepth.set(nodeIdx, depth)
    orderedNodes.push(nodeIdx)
    const children = (childrenOf.get(nodeIdx) ?? [])
      .sort((a, b) => {
        const aIsLeaf = !childrenOf.has(a.child)
        const bIsLeaf = !childrenOf.has(b.child)
        if (aIsLeaf !== bIsLeaf) return aIsLeaf ? -1 : 1
        return b.linkValue - a.linkValue
      })
    for (const { child } of children) dfs(child, depth + 1)
  }

  data.nodes
    .map((_, i) => i)
    .filter(i => sourceSet.has(i) && !targetSet.has(i))
    .sort((a, b) => data.nodes[b].value - data.nodes[a].value)
    .forEach(i => dfs(i, 0))

  const maxAvailableDepth = orderedNodes.length > 0
    ? Math.max(...orderedNodes.map(i => nodeDepth.get(i) ?? 0))
    : 1
  const depth = Math.min(maxDepth, maxAvailableDepth)

  const filteredNodes = orderedNodes.filter(i => (nodeDepth.get(i) ?? 0) <= depth)
  const filteredNodeSet = new Set(filteredNodes)
  const filteredLinks = data.links.filter(
    l => filteredNodeSet.has(l.source) && filteredNodeSet.has(l.target),
  )

  const nodePosition = new Map<number, number>()
  filteredNodes.forEach((nodeIdx, pos) => nodePosition.set(nodeIdx, pos))

  const sankeyData = {
    nodes: filteredNodes.map(i => ({ id: String(i) })),
    links: [...filteredLinks]
      .sort((a, b) => (nodePosition.get(a.target) ?? 0) - (nodePosition.get(b.target) ?? 0))
      .map(link => ({
        source: String(link.source),
        target: String(link.target),
        value: link.value,
      })),
  }

  // Derive chart height so:
  //  • every inter-node gap is NODE_SPACING px (py is never capped by d3-sankey)
  //  • the tallest right-column node is at most MAX_NODE_PX px tall
  //
  // d3-sankey sets ky = (innerH − gaps) / Σvalues.
  // We want ky = MAX_NODE_PX / maxRightValue, so innerH = ky·Σvalues + gaps.
  const rightColCount   = new Set(filteredLinks.map(l => l.target)).size
  // Cap based on the single largest node across BOTH columns — the same ky applies
  // to left and right, so Einzahlung (left) can be taller than Regulár (right).
  const maxNodeValue   = filteredNodes.length > 0
    ? Math.max(...filteredNodes.map(i => data.nodes[i].value))
    : 1
  const totalLinkValue = filteredLinks.reduce((s, l) => s + l.value, 0)

  const ky          = MAX_NODE_PX / maxNodeValue
  const bodyBudget  = Math.round(ky * totalLinkValue)
  const gapBudget   = (rightColCount - 1) * NODE_SPACING
  const chartHeight = Math.max(800, bodyBudget + gapBudget + MARGINS)

  const clickableLabel = useCallback<SankeyLabelComponent<DefaultNode, DefaultLink>>(
    ({ node, children, style, ...textProps }) => {
      const original = data.nodes[Number(node.id)]
      // nivo hard-codes pointerEvents:"none" in the style it passes here —
      // override it so mouse events reach the text element.
      const activeStyle = { ...(style as React.CSSProperties), pointerEvents: 'auto' } as typeof style
      // eslint-disable-next-line react-hooks/rules-of-hooks
      const { showTooltipFromEvent, hideTooltip } = useTooltip()
      const tooltip = <NodeTooltipContent node={node} />
      return (
        <Text
          {...textProps}
          style={activeStyle}
          cursor={onNodeClick ? 'pointer' : 'default'}
          onClick={onNodeClick && original ? () => onNodeClick(original) : undefined}
          onMouseEnter={(e) => { showTooltipFromEvent(tooltip, e) }}
          onMouseMove={(e) => { showTooltipFromEvent(tooltip, e) }}
          onMouseLeave={() => { hideTooltip() }}
        >
          {children}
        </Text>
      )
    },
    [data.nodes, onNodeClick],
  )

  return (
    <div style={{ width: '100%' }}>
      {maxAvailableDepth > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, marginBottom: 8 }}>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setMaxDepth(d => Math.max(1, d - 1))}
            disabled={depth <= 1}
          >
            <Minus />
          </Button>
          <span style={{ minWidth: 64, textAlign: 'center', fontSize: 13 }}>
            {t('sankey.depth', { depth })}
          </span>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setMaxDepth(d => d + 1)}
            disabled={depth >= maxAvailableDepth}
          >
            <Plus />
          </Button>
        </div>
      )}
      <div style={{ width: '100%', height: chartHeight, cursor: onNodeClick ? 'default' : undefined }}>
        <ResponsiveSankey
          data={sankeyData}
          label={node => data.nodes[Number(node.id)]?.name || '—'}
          margin={{ top: 32, right: 232, bottom: 32, left: 232 }}
          align="start"
          sort="input"
          colors={{ scheme: 'tableau10' }}
          nodeThickness={14}
          nodeSpacing={NODE_SPACING}
          nodeBorderWidth={0}
          nodeBorderRadius={2}
          linkOpacity={0.5}
          linkHoverOpacity={0.85}
          linkHoverOthersOpacity={0.1}
          linkBlendMode="normal"
          enableLinkGradient
          labelPosition="outside"
          labelPadding={14}
          labelTextColor={{ from: 'color', modifiers: [['brighter', 0.6]] }}
          labelComponent={clickableLabel}
          valueFormat={v => EUR.format(Number(v))}
          onClick={item => {
            if (!('id' in item)) return
            const original = data.nodes[Number(item.id)]
            if (original && onNodeClick) {
              onNodeClick(original)
            }
          }}
          nodeTooltip={({ node }) => <NodeTooltipContent node={node} />}
          theme={nivoTheme}
        />
      </div>
    </div>
  )
}
