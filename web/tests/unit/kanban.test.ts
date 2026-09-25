// @vitest-environment node
import { advanceLabel, defaultHistoryRange, nextStatus } from '@/lib/kanban'

describe('kanban helpers', () => {
  it('nextStatus follows NEW -> PREPARING -> READY -> COMPLETED', () => {
    expect(nextStatus('NEW')).toBe('PREPARING')
    expect(nextStatus('PREPARING')).toBe('READY')
    expect(nextStatus('READY')).toBe('COMPLETED')
    for (const s of ['PENDING', 'COMPLETED', 'CANCELLED'] as const) expect(nextStatus(s)).toBeNull()
  })

  it('advanceLabel names the next step per column', () => {
    expect(advanceLabel('NEW')).toBe('Start')
    expect(advanceLabel('PREPARING')).toBe('Ready')
    expect(advanceLabel('READY')).toBe('Complete')
    expect(advanceLabel('COMPLETED')).toBeNull()
  })

  it('defaultHistoryRange covers the last 30 days as UTC YYYY-MM-DD (matches the API)', () => {
    const { from, to } = defaultHistoryRange(new Date(Date.UTC(2026, 8, 25, 23, 30)))
    expect(to).toBe('2026-09-25')
    expect(from).toBe('2026-08-26')
    expect(defaultHistoryRange().to).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
