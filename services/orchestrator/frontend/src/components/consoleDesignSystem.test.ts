import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

function source(file: string): string {
  return readFileSync(fileURLToPath(new URL(file, import.meta.url)), 'utf8')
}

describe('Console design system', () => {
  it('provides loading, real error, retry and empty states', () => {
    const value = source('./AsyncState.vue')
    expect(value).toContain('<el-skeleton')
    expect(value).toContain(':title="error"')
    expect(value).toContain("$emit('retry')")
    expect(value).toContain('<el-empty')
  })

  it('provides shared card, section and technical ID primitives', () => {
    expect(source('./ConsoleCard.vue')).toContain('console-card__header')
    expect(source('./SectionHeader.vue')).toContain('section-header')
    expect(source('./TechnicalId.vue')).toContain('technical-id')
  })

  it('groups Agent capabilities while retaining legacy destinations', () => {
    const value = source('./AgentSubnav.vue')
    for (const route of ['/agents', '/agent-market', '/agent-metrics', '/agent-flow']) {
      expect(value).toContain(route)
    }
  })
})
