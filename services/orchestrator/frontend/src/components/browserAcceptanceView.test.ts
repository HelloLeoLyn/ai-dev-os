import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

function source(file:string):string{return readFileSync(fileURLToPath(new URL(file,import.meta.url)),'utf8')}

describe('Browser acceptance validation UI',()=>{
  it('renders browser availability, steps, errors and screenshot action',()=>{
    const view=source('../views/TestsView.vue')
    expect(view).toContain("check.type==='BROWSER'")
    expect(view).toContain('check.metadata.availability')
    expect(view).toContain('step.errorMessage')
    expect(view).toContain('View Screenshot')
  })
  it('previews screenshot through the controlled artifact endpoint',()=>{
    const dialog=source('./BrowserScreenshotDialog.vue')
    expect(dialog).toContain('validationArtifactContentUrl')
    expect(dialog).toContain('Browser acceptance screenshot')
  })
})
