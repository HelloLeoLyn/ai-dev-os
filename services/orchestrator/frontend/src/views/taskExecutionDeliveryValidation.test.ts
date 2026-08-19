import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./TaskExecutionView.vue', import.meta.url), 'utf8')

describe('delivery validation rerun UX', () => {
  it('A: no run shows Run Validation with first-run label', () => {
    expect(source).toContain("v-if=\"!deliveryValidation || deliveryValidation.status === 'FAILED' || deliveryGate?.decision === 'BLOCK'\"")
    expect(source).toContain("? 'Re-run Validation' : 'Run Validation'")
    expect(source).toContain("@click=\"runDeliveryValidation(change)\"")
  })

  it('B: FAILED run shows Re-run Validation', () => {
    expect(source).toContain("deliveryValidation?.status === 'FAILED' || deliveryGate?.decision === 'BLOCK' ? 'Re-run Validation'")
  })

  it('C: SUCCESS without gate still shows Evaluate Quality Gate', () => {
    expect(source).toContain("v-if=\"deliveryValidation?.status === 'SUCCESS' && !deliveryGate\"")
    expect(source).toContain("@click=\"evaluateDeliveryGate\"")
  })

  it('D: SUCCESS + BLOCK shows Re-run Validation and keeps Commit disabled', () => {
    expect(source).toContain("deliveryValidation?.status === 'FAILED' || deliveryGate?.decision === 'BLOCK' ? 'Re-run Validation'")
    expect(source).toContain(":disabled=\"deliveryGate?.decision !== 'PASS'\"")
  })

  it('E: REQUIRE_APPROVAL shows Approve / Reject Quality Gate', () => {
    expect(source).toContain("v-if=\"deliveryGate?.decision === 'REQUIRE_APPROVAL'\"")
    expect(source).toContain("@click=\"decideDeliveryGate(true)\"")
    expect(source).toContain("@click=\"decideDeliveryGate(false)\"")
  })

  it('F: PASS hides Re-run and enables Commit', () => {
    expect(source).toContain(":disabled=\"deliveryGate?.decision !== 'PASS'\"")
    expect(source).toContain("deliveryGate?.decision === 'BLOCK' ? 'Re-run Validation' : 'Run Validation'")
  })

  it('G: selects the newest gate by createdAt instead of repository order [0]', () => {
    expect(source).toContain("b.createdAt.localeCompare(a.createdAt))[0]")
    expect(source).not.toContain("getQualityGates(deliveryValidation.value.validationRunId).catch(() => []))[0]")
  })

  it('H: re-run switches to the freshly created ValidationRun and clears the stale gate', () => {
    expect(source).toContain("deliveryValidation.value = await startDeliveryValidation(change.changeId); deliveryGate.value = null")
    expect(source).toContain("deliveryValidation.value = validationRuns.filter(run => run.delivery).sort((a, b) => b.startedAt.localeCompare(a.startedAt))[0] ?? null")
  })
})
