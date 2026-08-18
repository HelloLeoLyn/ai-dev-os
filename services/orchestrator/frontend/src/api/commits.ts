import { apiClient } from './client'
export interface CommitRecord { commitId:string; taskId:string; changeId:string; branch:string; gitHash?:string; status:string }
export function getTaskCommits(taskId:string):Promise<CommitRecord[]> { return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/commits`) }
