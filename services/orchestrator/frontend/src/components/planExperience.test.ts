import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const approvalSource = readFileSync(fileURLToPath(new URL('./PlanApprovalDetail.vue', import.meta.url)), 'utf8')
const planViewSource = readFileSync(fileURLToPath(new URL('../views/TaskPlanView.vue', import.meta.url)), 'utf8')

describe('Plan experience structure', () => {
  it('renders the decision-oriented Plan modules and collapsible Goal', () => {
    for (const label of ['Approval Status', 'Goal · AI 准备做什么', 'Execution Plan · 如何执行',
      'Security · 是否安全', 'Advanced', 'Approval']) {
      expect(approvalSource).toContain(label)
    }
    expect(approvalSource).toContain('goal-content--collapsed')
    expect(approvalSource).toContain('展开完整 Goal')
    expect(approvalSource).toContain('Raw metadata')
  })

  it('keeps approval busy protection and required rejection validation', () => {
    expect(approvalSource).toContain(':loading="busy"')
    expect(approvalSource).toContain(':disabled="!canDecide(approval, busy)"')
    expect(approvalSource).toContain("Reject 必须填写原因。")
  })

  it('renders RUNNING, SUCCESS and FAILED feedback with result actions', () => {
    expect(planViewSource).toContain('任务正在执行...')
    expect(planViewSource).toContain('任务执行成功')
    expect(planViewSource).toContain('任务执行失败')
    expect(planViewSource).toContain('visibleTask.errorMessage')
    expect(planViewSource).toContain('查看 Timeline')
    expect(planViewSource).toContain("ElMessage.success('Plan 已批准，任务开始执行')")
  })
})
