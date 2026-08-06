import { apiClient } from './client'
import type { CreateProjectRequest, Project } from '../types/project'

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
