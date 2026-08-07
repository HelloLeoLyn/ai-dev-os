<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { disableSkill, enableSkill, getSkills } from '../api/skills'
import { getAgents } from '../api/agents'
import { useRegistryList } from '../composables/useRegistryList'
import SkillDetail from '../components/SkillDetail.vue'
import SkillTable from '../components/SkillTable.vue'
import type { AgentDefinition } from '../types/agent'
import type { Skill } from '../types/skill'

const agents = ref<AgentDefinition[]>([])
const {
  items: skills,
  selected: selectedSkill,
  loading,
  errorMessage,
  reload,
} = useRegistryList<Skill>({
  fetch: async () => {
    const [loadedSkills, loadedAgents] = await Promise.all([getSkills(), getAgents()])
    agents.value = loadedAgents
    return loadedSkills
  },
  idOf: (skill) => skill.skillId,
  errorText: '无法加载 Skills。',
})

const enabledCount = computed(() => skills.value.filter((skill) => skill.enabled).length)

function boundAgentsFor(skill: Skill | null): string[] {
  if (!skill) {
    return []
  }
  return agents.value
    .filter((agent) => agent.skillIds?.includes(skill.skillId))
    .map((agent) => agent.name ?? '')
    .filter((name) => name.length > 0)
}

async function handleToggle(skill: Skill, enable: boolean): Promise<void> {
  const action = enable ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${action}Skill「${skill.name}」？`, '权限确认', {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: enable ? 'info' : 'warning',
    })
  } catch {
    return
  }
  try {
    selectedSkill.value = enable
      ? await enableSkill(skill.skillId)
      : await disableSkill(skill.skillId)
    ElMessage.success(`Skill「${skill.name}」已${action}。`)
    await reload()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${action}Skill 失败。`)
  }
}
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Agent Skill System</p>
        <h1>Skills</h1>
        <p class="page-description">
          Agent 技能管理：加载、复用编码 / 测试 / 浏览器 / 部署等技能能力，并绑定到 Agent。
        </p>
      </div>
      <el-tag type="info" effect="dark">
        {{ enabledCount }} / {{ skills.length }} enabled
      </el-tag>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <SkillTable
            :skills="skills"
            :loading="loading"
            :selected-skill-id="selectedSkill?.skillId ?? null"
            @select="selectedSkill = $event"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <SkillDetail
          :skill="selectedSkill"
          :bound-agents="boundAgentsFor(selectedSkill)"
          @enable="handleToggle($event, true)"
          @disable="handleToggle($event, false)"
        />
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.content-row {
  margin-top: 0;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
