const { chromium } = require('playwright')

const MOCK_SERIES = [
  { id: 1, label: 'Netflix', type: 'SUBSCRIPTION', direction: 'EXPENSE', cadence: 'MONTHLY', intervalDays: 30, expectedAmount: 12.99, amountVariable: false, currency: 'EUR', accountIban: 'DE001', firstSeen: '2024-01-01', lastSeen: '2026-07-05', occurrenceCount: 19, nextExpectedDate: '2026-08-05', status: 'DETECTED', fingerprint: 'fp1', isFalsePositive: false, deviation: 'ON_TRACK', occurrences: [] },
  { id: 2, label: 'Miete', type: 'RENT', direction: 'EXPENSE', cadence: 'MONTHLY', intervalDays: 30, expectedAmount: 1200, amountVariable: false, currency: 'EUR', accountIban: 'DE001', firstSeen: '2023-01-01', lastSeen: '2026-07-01', occurrenceCount: 43, nextExpectedDate: '2026-08-10', status: 'DETECTED', fingerprint: 'fp2', isFalsePositive: false, deviation: 'ON_TRACK', occurrences: [] },
  { id: 3, label: 'Gehalt', type: 'SALARY', direction: 'INCOME', cadence: 'MONTHLY', intervalDays: 30, expectedAmount: 3500, amountVariable: true, currency: 'EUR', accountIban: 'DE001', firstSeen: '2023-01-25', lastSeen: '2026-07-25', occurrenceCount: 43, nextExpectedDate: '2026-08-25', status: 'DETECTED', fingerprint: 'fp3', isFalsePositive: false, deviation: 'ON_TRACK', occurrences: [] },
  { id: 5, label: 'FP-Spam', type: 'OTHER', direction: 'EXPENSE', cadence: 'MONTHLY', intervalDays: 30, expectedAmount: 99, amountVariable: false, currency: 'EUR', accountIban: 'DE001', firstSeen: '2024-01-01', lastSeen: '2026-07-01', occurrenceCount: 5, nextExpectedDate: '2026-08-10', status: 'DETECTED', fingerprint: 'fp5', isFalsePositive: true, deviation: 'ON_TRACK', occurrences: [] },
]

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.setViewportSize({ width: 1280, height: 900 })

  await context.route('http://localhost:5274/auth/me', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ username: 'testuser', isSystemAdmin: false, activeOrganizationId: 1, organizations: [{ id: 1, name: 'Persönlich', role: 'OWNER', logoUrl: null }], impersonating: null }) }))
  await context.route('http://localhost:5274/transactions/recurring', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_SERIES) }))

  await page.goto('http://localhost:5274/wiederkehrer', { waitUntil: 'networkidle', timeout: 15000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: '/tmp/rcr-01-initial.png' })
  console.log('Screenshot 1: initial recurring page (table view)')

  // Step 1: Check view toggle buttons
  const tableBtn = page.locator('button', { hasText: 'Tabelle' }).first()
  const timelineBtn = page.locator('button', { hasText: 'Zeitstrahl' }).first()
  const tableBtnVis = await tableBtn.isVisible()
  const tlBtnVis = await timelineBtn.isVisible()
  console.log('✅ Table button visible:', tableBtnVis)
  console.log('✅ Timeline button visible:', tlBtnVis)

  // Table should be visible by default
  const tableVisible = await page.locator('.rcr-table').isVisible()
  console.log('✅ Table (rcr-table) visible by default:', tableVisible)

  // Step 2: Switch to timeline
  await timelineBtn.click()
  await page.waitForTimeout(500)
  await page.screenshot({ path: '/tmp/rcr-02-timeline.png' })
  console.log('Screenshot 2: timeline view')

  const rangeVisible = await page.locator('.rcr-tl-range-selector').isVisible()
  console.log('✅ Range selector visible:', rangeVisible)

  const tlContainerVisible = await page.locator('.rcr-tl-container').isVisible()
  console.log('✅ Timeline container visible:', tlContainerVisible)

  for (const label of ['30 Tage', '60 Tage', '90 Tage', '180 Tage']) {
    const visible = await page.locator('button', { hasText: label }).first().isVisible()
    console.log(`  ${label} button: ${visible}`)
  }

  // Step 3: Verify cards and filtering
  const cards90 = await page.locator('.rcr-tl-card').count()
  console.log('✅ Cards at default 90d:', cards90, '(3 series × 3mo ≈ 9)')

  // Check today dot
  const todayDotCount = await page.locator('.rcr-tl-dot--today').count()
  console.log('✅ Today dot count (Netflix nextExpected=today):', todayDotCount, '≥1:', todayDotCount >= 1)

  // Step 4: 180d vs 30d comparison
  await page.locator('button', { hasText: '180 Tage' }).first().click()
  await page.waitForTimeout(300)
  const cards180 = await page.locator('.rcr-tl-card').count()
  await page.screenshot({ path: '/tmp/rcr-03-180days.png', fullPage: true })
  console.log('Screenshot 3: 180 days')
  console.log('✅ Cards at 180d:', cards180)

  await page.locator('button', { hasText: '30 Tage' }).first().click()
  await page.waitForTimeout(300)
  const cards30 = await page.locator('.rcr-tl-card').count()
  await page.screenshot({ path: '/tmp/rcr-04-30days.png' })
  console.log('Screenshot 4: 30 days')
  console.log('✅ Cards at 30d:', cards30)
  console.log('✅ 180d shows more than 30d:', cards180 > cards30)

  // Step 5: false positive excluded
  const allLabels = await page.locator('.rcr-tl-card-label').allInnerTexts()
  const fpExcluded = !allLabels.includes('FP-Spam')
  console.log('✅ isFalsePositive excluded:', fpExcluded, '| labels found:', allLabels.join(', '))

  // Step 6: Toggle back to table
  await tableBtn.click()
  await page.waitForTimeout(300)
  await page.screenshot({ path: '/tmp/rcr-05-table.png' })
  console.log('Screenshot 5: back to table')
  const tableBackVisible = await page.locator('.rcr-table').isVisible()
  const tlGone = (await page.locator('.rcr-tl-container').count()) === 0
  console.log('✅ Table visible after switching back:', tableBackVisible)
  console.log('✅ Timeline hidden after switching back:', tlGone)

  // Probe: Zeitstrahl button while in pending mode (if any) — not testable without full BE
  // Probe: Direction filter affects timeline
  await timelineBtn.click()
  await page.waitForTimeout(300)
  await page.locator('button', { hasText: '90 Tage' }).first().click()
  await page.waitForTimeout(200)
  await page.locator('button', { hasText: 'Ausgaben' }).first().click()
  await page.waitForTimeout(300)
  const cardsExpenseOnly = await page.locator('.rcr-tl-card').count()
  await page.locator('button', { hasText: 'Alle' }).first().click()
  await page.waitForTimeout(300)
  const cardsAll = await page.locator('.rcr-tl-card').count()
  console.log('🔍 Ausgaben filter: cards =', cardsExpenseOnly, '/ all cards =', cardsAll, '| filtered < all:', cardsExpenseOnly < cardsAll)

  await page.screenshot({ path: '/tmp/rcr-06-direction-filter.png' })

  await browser.close()
  console.log('Done.')
})()
