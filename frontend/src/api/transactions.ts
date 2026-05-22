export interface SankeyNode {
  name: string
  value: number
}

export interface SankeyLink {
  source: number
  target: number
  value: number
}

export interface SankeyResponse {
  nodes: SankeyNode[]
  links: SankeyLink[]
}

export async function fetchSankeyData(from: string, to: string): Promise<SankeyResponse> {
  const res = await fetch(`/transactions/sankey?from=${from}&to=${to}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SankeyResponse>
}
