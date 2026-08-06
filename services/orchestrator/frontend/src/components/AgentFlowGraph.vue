<script setup lang="ts">
import type { AgentExecutionPlan, AgentPlanStatus } from '../types/agentPlan'

const props = defineProps<{
  steps: AgentExecutionPlan[] | null
}>()

const pipeline = [
  { agentId: 'hermes', name: 'Hermes', role: '规划 / 任务分析', icon: '🧭' },
  { agentId: 'codex', name: 'Codex', role: '编码 / 代码生成', icon: '💻' },
  { agentId: 'openclaw', name: 'OpenClaw', role: '浏览器测试', icon: '🌐' },
  { agentId: 'testagent', name: 'TestAgent', role: '测试验证', icon: '✅' },
]

function statusTone(status: AgentPlanStatus): 'success' | 'danger' | 'info' | 'warning' {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'warning'
    default:
      return 'info'
  }
}

function stepFor(agentId: string): AgentExecutionPlan | undefined {
  return props.steps?.find((step) => step.agentId === agentId)
}
</script>

<template>
  <div class="flow">
    <template v-for="(agent, index) in pipeline" :key="agent.agentId">
      <div
        class="flow-node"
        :class="{
          'flow-node--idle': !stepFor(agent.agentId),
          'flow-node--failed': stepFor(agent.agentId)?.status === 'FAILED',
          'flow-node--success': stepFor(agent.agentId)?.status === 'SUCCESS',
        }"
      >
        <div class="flow-node__icon">{{ agent.icon }}</div>
        <div class="flow-node__body">
          <div class="flow-node__header">
            <span class="flow-node__name">{{ agent.name }}</span>
            <el-tag
              v-if="stepFor(agent.agentId)"
              :type="statusTone(stepFor(agent.agentId)!.status)"
              effect="dark"
              size="small"
            >
              {{ stepFor(agent.agentId)!.status }}
            </el-tag>
            <el-tag v-else type="info" size="small">未参与</el-tag>
          </div>
          <p class="flow-node__role">{{ agent.role }}</p>
          <p v-if="stepFor(agent.agentId)?.result" class="flow-node__result">
            {{ stepFor(agent.agentId)!.result }}
          </p>
        </div>
      </div>
      <div v-if="index < pipeline.length - 1" class="flow-arrow">↓</div>
    </template>
  </div>
</template>

<style scoped>
.flow {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0.25rem;
}

.flow-node {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: 0.5rem;
  background: var(--color-surface, #ffffff);
  transition: border-color 0.2s;
}

.flow-node--success {
  border-color: var(--color-success, #67c23a);
}

.flow-node--failed {
  border-color: var(--color-danger, #f56c6c);
}

.flow-node--idle {
  opacity: 0.55;
}

.flow-node__icon {
  font-size: 1.5rem;
}

.flow-node__body {
  min-width: 0;
  flex: 1;
}

.flow-node__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.flow-node__name {
  font-weight: 700;
}

.flow-node__role {
  margin: 0.15rem 0 0;
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.flow-node__result {
  margin: 0.35rem 0 0;
  font-size: 0.8rem;
  overflow-wrap: anywhere;
}

.flow-arrow {
  align-self: center;
  color: var(--color-text-muted);
  font-size: 1.1rem;
  line-height: 1;
}
</style>
