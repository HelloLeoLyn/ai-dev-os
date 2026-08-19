import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const source = (path: string) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8')
const tasksView = source('./TasksView.vue')
const taskTypes = source('../types/task.ts')
const duplicate = source('../services/taskDuplicate.ts')

describe('Task model selection (WP2)', () => {
  it('loads enabled model definitions from the registry for the dropdown', () => {
    expect(tasksView).toContain("modelRegistryApi.listModels()")
    expect(tasksView).toContain('modelOptions')
    expect(tasksView).toContain('model.enabled')
    expect(tasksView).toContain('label="Auto（Agent 默认模型）"')
  })

  it('submits only requestedModelId, never provider/baseUrl/credential', () => {
    expect(tasksView).toContain('requestedModelId')
    expect(tasksView).not.toContain('form.providerId')
    expect(tasksView).not.toContain('form.baseUrl')
    expect(tasksView).not.toContain('credential')
  })

  it('declares requestedModelId as an optional create payload field', () => {
    expect(taskTypes).toContain('requestedModelId?: string | null')
  })

  it('remembers the model selection for task duplicates', () => {
    expect(duplicate).toContain('requestedModelId: request.requestedModelId ?? null')
    expect(duplicate).toContain("requestedModelId: metadata?.requestedModelId || ''")
  })

  it('keeps Auto as the default when nothing is selected', () => {
    expect(tasksView).toContain("requestedModelId: ''")
  })
})
