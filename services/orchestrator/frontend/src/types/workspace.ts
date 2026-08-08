export type WorkspaceStatus = 'READY' | 'LOCKED' | 'CLEANUP' | 'FAILED'

export interface Workspace {
  workspaceId: string
  projectId: string
  path: string
  branch: string | null
  status: WorkspaceStatus
  createdAt: string
  updatedAt: string
}

export interface CreateWorkspaceRequest {
  projectId: string
  path: string
}

export interface GitStatus {
  branch: string
  modified: number
  added: number
  deleted: number
}

export interface GitDiff {
  filesChanged: number
  insertions: number
  deletions: number
  stat: string
}
