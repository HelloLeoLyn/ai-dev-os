import { apiClient } from './client'
export interface CommitRecord { commitId:string; taskId:string; changeId:string; branch:string; gitHash?:string; status:string }
export function getTaskCommits(taskId:string):Promise<CommitRecord[]> { return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/commits`) }
export function recoverCommit(taskId:string, commitId:string):Promise<CommitRecord> { return apiClient.post(`/api/tasks/${encodeURIComponent(taskId)}/commits/${encodeURIComponent(commitId)}/recover`) }
