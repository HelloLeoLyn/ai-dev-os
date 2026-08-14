import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
const source=readFileSync(resolve(dirname(fileURLToPath(import.meta.url)),'NetworkSettingsView.vue'),'utf8')
describe('Network / Proxy settings',()=>{
  it('supports modes, auto host, probes and masked credential flow',()=>{
    expect(source).toContain("DIRECT")
    expect(source).toContain("SYSTEM")
    expect(source).toContain("CUSTOM")
    expect(source).toContain("AUTO_WINDOWS_HOST")
    expect(source).toContain("Run Network Probe")
    expect(source).toContain("localhost, 127.0.0.1 and ::1 are always DIRECT")
    expect(source.match(/type="password"/g)).toHaveLength(3)
    expect(source).not.toContain('show-password')
  })
})
