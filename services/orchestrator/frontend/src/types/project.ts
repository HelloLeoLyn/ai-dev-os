export type ProjectStatus = 'ACTIVE' | 'DISABLED' | 'ARCHIVED'

export interface Project {
  projectId: string
  name: string
  path: string
  description: string | null
  repositoryUrl: string | null
  defaultBranch: string | null
  status: ProjectStatus
  createdAt: string
  updatedAt: string
}

export interface CreateProjectRequest {
  name: string
  path: string
  description?: string
  repositoryUrl?: string
  defaultBranch?: string
}
