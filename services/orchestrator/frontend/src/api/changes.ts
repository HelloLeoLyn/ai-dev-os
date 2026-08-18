import { apiClient } from './client'

export interface ChangeSet { changeId:string; taskId:string; workspaceId:string; projectId:string; executionId:string; branch:string; diff:string; diffStat:string; filesChanged:number; status:string }
export function getTaskChanges(taskId:string):Promise<ChangeSet[]> { return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/changes`) }
export function reviewChange(id:string):Promise<ChangeSet> { return apiClient.post(`/api/changes/${encodeURIComponent(id)}/review`) }
export function approveChange(id:string):Promise<ChangeSet> { return apiClient.post(`/api/changes/${encodeURIComponent(id)}/approve`) }
export function rejectChange(id:string):Promise<ChangeSet> { return apiClient.post(`/api/changes/${encodeURIComponent(id)}/reject`) }
export function commitChange(id:string):Promise<unknown> { return apiClient.post(`/api/changes/${encodeURIComponent(id)}/commit`) }
export function retryChangeProjection(taskId:string):Promise<ChangeSet[]> { return apiClient.post(`/api/tasks/${encodeURIComponent(taskId)}/changes/projection/retry`) }
