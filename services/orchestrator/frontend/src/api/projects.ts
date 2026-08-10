import { apiClient } from './client'
import type { AgentMetrics } from '../types/agentMetrics'
import type { CreateProjectRequest, Project } from '../types/project'
import type { TaskRecord } from '../types/task'
import type { Workspace } from '../types/workspace'

export function getProjects(): Promise<Project[]> {
  return apiClient.get<Project[]>('/api/projects')
}

export function getProject(projectId: string): Promise<Project> {
  return apiClient.get<Project>(`/api/projects/${encodeURIComponent(projectId)}`)
}

export function createProject(request: CreateProjectRequest): Promise<Project> {
  return apiClient.post<Project>('/api/projects', request)
}

export function setProjectActive(projectId: string): Promise<Project> {
  return apiClient.post<Project>(`/api/projects/${encodeURIComponent(projectId)}/active`)
}

export function archiveProject(projectId: string): Promise<Project> {
  return apiClient.post<Project>(`/api/projects/${encodeURIComponent(projectId)}/archive`)
}

export function getProjectWorkspaces(projectId: string): Promise<Workspace[]> {
  return apiClient.get<Workspace[]>(
    `/api/projects/${encodeURIComponent(projectId)}/workspaces`,
  )
}

export function getProjectTasks(projectId: string): Promise<TaskRecord[]> {
  return apiClient.get<TaskRecord[]>(
    `/api/projects/${encodeURIComponent(projectId)}/tasks`,
  )
}

export function getProjectMetrics(projectId: string): Promise<AgentMetrics[]> {
  return apiClient.get<AgentMetrics[]>(
    `/api/projects/${encodeURIComponent(projectId)}/metrics`,
  )
}
