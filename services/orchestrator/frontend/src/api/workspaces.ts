import { apiClient } from './client'
import type { CreateWorkspaceRequest, GitDiff, GitStatus, Workspace } from '../types/workspace'

export function getWorkspaces(): Promise<Workspace[]> {
  return apiClient.get<Workspace[]>('/api/workspaces')
}

export function getWorkspace(workspaceId: string): Promise<Workspace> {
  return apiClient.get<Workspace>(`/api/workspaces/${encodeURIComponent(workspaceId)}`)
}

export function createWorkspace(request: CreateWorkspaceRequest): Promise<Workspace> {
  return apiClient.post<Workspace>('/api/workspaces', request)
}

export function getWorkspaceGitStatus(workspaceId: string): Promise<GitStatus> {
  return apiClient.get<GitStatus>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/git/status`,
  )
}

export function getWorkspaceGitDiff(workspaceId: string): Promise<GitDiff> {
  return apiClient.get<GitDiff>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}/git/diff`,
  )
}
