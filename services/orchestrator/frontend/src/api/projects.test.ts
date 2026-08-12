import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from './client'
import { createProject, createProjectTask, createProjectWorkspace } from './projects'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('createProjectWorkspace', () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset()
  })

  it('creates a workspace in the current project scope using its selected path', async () => {
    const workspace = {
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      path: '/repo/demo',
      branch: 'dev',
      status: 'READY' as const,
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    }
    vi.mocked(apiClient.post).mockResolvedValue(workspace)

    await expect(createProjectWorkspace('project-1', '/repo/demo')).resolves.toEqual(workspace)
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/projects/project-1/workspaces',
      { path: '/repo/demo' },
    )
  })

  it('allows the backend to fall back to Project.path', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({})

    await createProjectWorkspace('project/one')

    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/projects/project%2Fone/workspaces',
      { path: undefined },
    )
  })
})

describe('createProject', () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset()
  })

  it('does not require users to provide repository metadata', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({})
    const request = { name: 'demo', path: '/repo/demo', description: 'Demo' }

    await createProject(request)

    expect(apiClient.post).toHaveBeenCalledWith('/api/projects', request)
  })
})

describe('createProjectTask', () => {
  it('creates a read-only task in the URL project scope with its workspace', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({})
    const request = {
      name: '分析 JJX 项目现状', description: '只读分析', goal: '分析',
      plannerName: 'hermes', projectId: 'untrusted-body-project',
      workspaceId: 'workspace-jjx', executionMode: 'READ_ONLY' as const,
    }

    await createProjectTask('project-jjx', request)

    expect(apiClient.post).toHaveBeenCalledWith('/api/projects/project-jjx/tasks', request)
  })
})
