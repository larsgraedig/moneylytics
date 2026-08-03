import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { ResponsiveSankey } from '@nivo/sankey'
import type { DefaultLink, DefaultNode, SankeyLabelComponent, SankeyNodeDatum } from '@nivo/sankey'
import { Text } from '@nivo/text'
import { useTooltip } from '@nivo/tooltip'
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

function NodeTooltipContent({
  node,
  isDrillable,
}: {
  node: SankeyNodeDatum<DefaultNode, DefaultLink>
  isDrillable: boolean
}) {
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
      {isDrillable && (
        <span style={{ color: '#6b6b78', marginLeft: 4 }}>{t('sankey.clickToDrillDown')}</span>
      )}
    </div>
  )
}

export default function SankeyChart({ data, onNodeClick }: Props) {
  const sankeyData = {
    nodes: data.nodes.map((_, i) => ({ id: String(i) })),
    links: data.links.map(link => ({
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
  const rightColIndices = new Set(data.links.map(l => l.target))
  const rightColCount   = rightColIndices.size
  // Cap based on the single largest node across BOTH columns — the same ky applies
  // to left and right, so Einzahlung (left) can be taller than Regulár (right).
  const maxNodeValue   = Math.max(...data.nodes.map(n => n.value))
  const totalLinkValue = data.links.reduce((s, l) => s + l.value, 0)

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
      const tooltip = <NodeTooltipContent node={node} isDrillable={!!onNodeClick} />
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
    <div style={{ width: '100%', height: chartHeight, cursor: onNodeClick ? 'default' : undefined }}>
      <ResponsiveSankey
        data={sankeyData}
        label={node => data.nodes[Number(node.id)]?.name || '—'}
        margin={{ top: 32, right: 232, bottom: 32, left: 232 }}
        align="justify"
        sort="descending"
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
        nodeTooltip={({ node }) => <NodeTooltipContent node={node} isDrillable={!!onNodeClick} />}
        theme={nivoTheme}
      />
    </div>
  )
}
