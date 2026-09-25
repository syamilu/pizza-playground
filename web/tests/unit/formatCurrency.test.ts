// @vitest-environment node
//
// Price formatter helper (src/lib/utils.ts -> formatCurrency). Malaysian Ringgit,
// always two decimals, with a NaN guard. Assertions stay tolerant of ICU
// grouping/symbol spacing differences and focus on the contract that matters.

import { describe, it, expect } from 'vitest'
import { formatCurrency } from '@/lib/utils'

describe('formatCurrency', () => {
  it('returns a string', () => {
    expect(typeof formatCurrency(10)).toBe('string')
  })

  it('always renders exactly two decimal places', () => {
    expect(formatCurrency(12.99)).toMatch(/12\.99/)
    expect(formatCurrency(5)).toMatch(/5\.00/)
    expect(formatCurrency(0)).toMatch(/0\.00/)
  })

  it('includes the Ringgit symbol', () => {
    expect(formatCurrency(12.99)).toMatch(/RM/)
  })

  it('groups thousands', () => {
    // ms-MY uses comma grouping; the regex tolerates any single grouping char.
    expect(formatCurrency(1234.5)).toMatch(/1.234\.50/)
  })

  it('guards against NaN by formatting as zero', () => {
    expect(formatCurrency(Number.NaN)).toBe(formatCurrency(0))
  })

  it('guards against non-finite values', () => {
    expect(formatCurrency(Infinity)).toBe(formatCurrency(0))
    expect(formatCurrency(-Infinity)).toBe(formatCurrency(0))
  })

  it('formats negative amounts with two decimals', () => {
    expect(formatCurrency(-5)).toMatch(/5\.00/)
  })

  it('rounds to two decimals', () => {
    expect(formatCurrency(1.005)).toMatch(/1\.0[01]/)
    expect(formatCurrency(1.239)).toMatch(/1\.24/)
  })
})
