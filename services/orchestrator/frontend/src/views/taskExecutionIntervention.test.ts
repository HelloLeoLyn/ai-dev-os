import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./TaskExecutionView.vue', import.meta.url), 'utf8')

describe('human intervention UI', () => {
  it('NEEDS_INTERVENTION with FIX_CREDENTIAL shows Retry as primary action', () => {
    expect(source).toContain("v-if=\"interventionPrimary === 'RETRY'\"")
    expect(source).toContain("@click=\"runIntervention('RETRY')\"")
    expect(source).toContain('Retry')
  })

  it('recommendedAction REPLAN shows Replan as primary action', () => {
    expect(source).toContain("v-if=\"interventionPrimary === 'REPLAN'\"")
    expect(source).toContain("@click=\"runIntervention('REPLAN')\"")
  })

  it('clicking Retry calls the intervention API with RETRY', () => {
    expect(source).toContain("await intervene(runId, action)")
    expect(source).toContain("runIntervention('RETRY')")
  })

  it('clicking Replan calls the intervention API with REPLAN', () => {
    expect(source).toContain("runIntervention('REPLAN')")
    expect(source).toContain('intervene(runId, action)')
  })

  it('Abort requires confirmation before the intervention API is called', () => {
    expect(source).toContain("@click=\"runIntervention('ABORT')\"")
    const abortPath = source.slice(source.indexOf('if (requiresConfirmation(action))'))
    const beforeApi = abortPath.slice(0, abortPath.indexOf('await intervene(runId, action)'))
    expect(beforeApi).toContain('ElMessageBox.confirm')
  })
})
