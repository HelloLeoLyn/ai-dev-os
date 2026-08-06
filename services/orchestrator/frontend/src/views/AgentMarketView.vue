<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  getAgentPackages,
  installAgentPackage,
  uninstallAgentPackage,
} from '../api/agentMarket'
import AgentMarketTable from '../components/AgentMarketTable.vue'
import AgentPackageDetail from '../components/AgentPackageDetail.vue'
import type { AgentPackage } from '../types/agentPackage'

const packages = ref<AgentPackage[]>([])
const selectedPackage = ref<AgentPackage | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const installedCount = computed(
  () => packages.value.filter((agentPackage) => agentPackage.installed).length,
)

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = null
  try {
    packages.value = await getAgentPackages()
    if (!selectedPackage.value && packages.value.length > 0) {
      selectedPackage.value = packages.value[0]
    } else if (selectedPackage.value) {
      const refreshed = packages.value.find(
        (agentPackage) => agentPackage.agentId === selectedPackage.value?.agentId,
      )
      if (refreshed) {
        selectedPackage.value = refreshed
      }
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载 Agent 市场。'
  } finally {
    loading.value = false
  }
}

async function handleInstall(agentPackage: AgentPackage): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认安装 Agent「${agentPackage.name}」（v${agentPackage.version ?? '—'}）？` +
        '安装后将注册 AgentDefinition 并绑定 Skills。',
      '安装确认',
      { confirmButtonText: '安装', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  try {
    selectedPackage.value = await installAgentPackage(agentPackage.agentId)
    ElMessage.success(`Agent「${agentPackage.name}」已安装。`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '安装 Agent 失败。')
  }
}

async function handleUninstall(agentPackage: AgentPackage): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认卸载 Agent「${agentPackage.name}」？卸载后将移除已注册的 AgentDefinition。`,
      '卸载确认',
      { confirmButtonText: '卸载', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    selectedPackage.value = await uninstallAgentPackage(agentPackage.agentId)
    ElMessage.success(`Agent「${agentPackage.name}」已卸载。`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '卸载 Agent 失败。')
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Agent Ecosystem</p>
        <h1>Agent Market</h1>
        <p class="page-description">
          Agent 生态平台：浏览、安装、卸载 Agent 包，安装时自动注册 AgentDefinition、
          绑定 Skills 并校验 MCP Plugins。
        </p>
      </div>
      <el-tag type="info" effect="dark">
        {{ installedCount }} / {{ packages.length }} installed
      </el-tag>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <AgentMarketTable
            :packages="packages"
            :loading="loading"
            :selected-agent-id="selectedPackage?.agentId ?? null"
            @select="selectedPackage = $event"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <AgentPackageDetail
          :agent-package="selectedPackage"
          @install="handleInstall($event)"
          @uninstall="handleUninstall($event)"
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
