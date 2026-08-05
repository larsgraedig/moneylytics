export type Preset =
  | 'last30'
  | 'last3Months' | 'last6Months' | 'last12Months' | 'last24Months'
  | 'currentMonth' | 'lastMonth'
  | 'currentQuarter' | 'lastQuarter'
  | 'currentHalf' | 'lastHalf'
  | 'currentYear' | 'lastYear'

export const PRESETS: Preset[] = [
  'last30',
  'last3Months', 'last6Months', 'last12Months', 'last24Months',
  'currentMonth', 'lastMonth',
  'currentQuarter', 'lastQuarter',
  'currentHalf', 'lastHalf',
  'currentYear', 'lastYear',
]

function localIso(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function detectPreset(from: string, to: string): Preset | '' {
  return PRESETS.find(p => {
    const r = getPresetRange(p)
    return r.from === from && r.to === to
  }) ?? ''
}

export function getPresetRange(preset: Preset): { from: string; to: string } {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth()
  const q = Math.floor(m / 3)
  const h = m < 6 ? 0 : 1

  switch (preset) {
    case 'last30': {
      const to = new Date(now)
      to.setDate(to.getDate() - 1)
      const from = new Date(to)
      from.setDate(from.getDate() - 29)
      return { from: localIso(from), to: localIso(to) }
    }
    case 'last3Months': {
      const from = new Date(now)
      from.setMonth(from.getMonth() - 3)
      return { from: localIso(from), to: localIso(now) }
    }
    case 'last6Months': {
      const from = new Date(now)
      from.setMonth(from.getMonth() - 6)
      return { from: localIso(from), to: localIso(now) }
    }
    case 'last12Months': {
      const from = new Date(now)
      from.setFullYear(from.getFullYear() - 1)
      return { from: localIso(from), to: localIso(now) }
    }
    case 'last24Months': {
      const from = new Date(now)
      from.setFullYear(from.getFullYear() - 2)
      return { from: localIso(from), to: localIso(now) }
    }
    case 'currentMonth':
      return { from: localIso(new Date(y, m, 1)), to: localIso(new Date(y, m + 1, 0)) }
    case 'lastMonth':
      return { from: localIso(new Date(y, m - 1, 1)), to: localIso(new Date(y, m, 0)) }
    case 'currentQuarter':
      return { from: localIso(new Date(y, q * 3, 1)), to: localIso(new Date(y, q * 3 + 3, 0)) }
    case 'lastQuarter': {
      const lq = q === 0 ? 3 : q - 1
      const ly = q === 0 ? y - 1 : y
      return { from: localIso(new Date(ly, lq * 3, 1)), to: localIso(new Date(ly, lq * 3 + 3, 0)) }
    }
    case 'currentHalf':
      return { from: localIso(new Date(y, h * 6, 1)), to: localIso(new Date(y, h * 6 + 6, 0)) }
    case 'lastHalf': {
      const lh = 1 - h
      const ly = h === 0 ? y - 1 : y
      return { from: localIso(new Date(ly, lh * 6, 1)), to: localIso(new Date(ly, lh * 6 + 6, 0)) }
    }
    case 'currentYear':
      return { from: `${y}-01-01`, to: `${y}-12-31` }
    case 'lastYear':
      return { from: `${y - 1}-01-01`, to: `${y - 1}-12-31` }
  }
}
