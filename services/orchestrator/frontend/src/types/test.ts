export type TestType = 'UNIT_TEST' | 'API_TEST' | 'UI_TEST' | 'BUILD_VERIFY'

export type TestStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface TestPlan {
  testId: string
  taskId: string | null
  testType: TestType
  command: string
  projectId: string
  executionId: string | null
  createdAt: string
  status: TestStatus
  updatedAt: string
  startedAt: string | null
  completedAt: string | null
  result: string | null
  logs: string | null
  errorMessage: string | null
  screenshotPath: string | null
}

export interface CreateTestRequest {
  taskId?: string
  testType: TestType
  command?: string
  executionId?: string
  projectId?: string
}

export interface TestReport {
  testId: string
  summary: string | null
  passed: number
  failed: number
  duration: number
  artifacts: string[]
}
