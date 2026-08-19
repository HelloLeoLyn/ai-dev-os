import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const viewSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), 'ModelsView.vue'),
  'utf8',
)
const providerSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../components/ProviderTable.vue'),
  'utf8',
)
const modelSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../components/ModelTable.vue'),
  'utf8',
)
const apiSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../api/models.ts'),
  'utf8',
)

describe('Model / Provider registry UI', () => {
  it('renders provider and model configuration cards', () => {
    expect(viewSource).toContain('Provider')
    expect(viewSource).toContain('Model')
    expect(viewSource).toContain('Credential Ref')
    expect(viewSource).toContain('环境变量 / Secret 引用')
  })

  it('uses registry API endpoints for CRUD and enable/disable', () => {
    expect(apiSource).toContain('/api/model-registry/providers')
    expect(apiSource).toContain('/api/model-registry/models')
    expect(apiSource).toContain('/enabled')
    expect(apiSource).toContain('createProvider')
    expect(apiSource).toContain('updateProvider')
    expect(apiSource).toContain('setProviderEnabled')
    expect(apiSource).toContain('createModel')
    expect(apiSource).toContain('updateModel')
    expect(apiSource).toContain('setModelEnabled')
  })

  it('keeps credential ref an env-var reference, not a secret input', () => {
    expect(providerSource).toContain('Credential Ref（环境变量 / Secret 引用）')
    expect(providerSource).toContain('DEEPSEEK_API_KEY')
    expect(providerSource).not.toContain('type="password"')
    expect(providerSource).not.toContain('apiKey')
    expect(providerSource).not.toContain('secretValue')
    expect(providerSource).toContain('仅保存引用名称')
  })

  it('supports creating and editing providers', () => {
    expect(providerSource).toContain('New Provider')
    expect(providerSource).toContain('编辑')
    expect(providerSource).toContain('baseUrl')
    expect(providerSource).toContain('https://api.deepseek.com')
    expect(providerSource).toContain('el-switch')
  })

  it('supports creating and editing models with provider + executor binding', () => {
    expect(modelSource).toContain('New Model')
    expect(modelSource).toContain('deepseek-v4-flash')
    expect(modelSource).toContain('executorType')
    expect(modelSource).toContain('capabilities')
    expect(modelSource).toContain('codex')
    expect(modelSource).toContain('openclaw')
  })

  it('never submits secret values from the frontend', () => {
    const combined = `${providerSource}\n${modelSource}\n${viewSource}`
    expect(combined).not.toMatch(/apiKey|secretKey|accessToken\b/)
  })

  it('shows provider credential configured/missing status without secret values', () => {
    expect(providerSource).toContain('Configured')
    expect(providerSource).toContain('Missing')
    expect(providerSource).toContain('credentialConfigured')
    expect(viewSource).toContain('providerStatus')
    expect(apiSource).toContain('/default-model')
  })

  it('marks the coder default model with a Default badge', () => {
    expect(modelSource).toContain('defaultModelId')
    expect(modelSource).toContain('Default')
    expect(viewSource).toContain('defaultModelId')
  })
})
