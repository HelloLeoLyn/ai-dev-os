<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { disableMcpPlugin, enableMcpPlugin, getMcpPlugins } from '../api/mcpPlugins'
import PluginDetail from '../components/PluginDetail.vue'
import PluginTable from '../components/PluginTable.vue'
import type { McpPlugin } from '../types/mcpPlugin'

const plugins = ref<McpPlugin[]>([])
const selectedPlugin = ref<McpPlugin | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const enabledCount = computed(() => plugins.value.filter((plugin) => plugin.enabled).length)

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = null
  try {
    plugins.value = await getMcpPlugins()
    if (!selectedPlugin.value && plugins.value.length > 0) {
      selectedPlugin.value = plugins.value[0]
    } else if (selectedPlugin.value) {
      const refreshed = plugins.value.find(
        (plugin) => plugin.pluginId === selectedPlugin.value?.pluginId,
      )
      if (refreshed) {
        selectedPlugin.value = refreshed
      }
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载 MCP 插件。'
  } finally {
    loading.value = false
  }
}

async function handleToggle(plugin: McpPlugin, enable: boolean): Promise<void> {
  const action = enable ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${action}插件「${plugin.name}」？`, '权限确认', {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: enable ? 'info' : 'warning',
    })
  } catch {
    return
  }
  try {
    selectedPlugin.value = enable
      ? await enableMcpPlugin(plugin.pluginId)
      : await disableMcpPlugin(plugin.pluginId)
    ElMessage.success(`插件「${plugin.name}」已${action}。`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${action}插件失败。`)
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">MCP Plugin System</p>
        <h1>MCP Plugins</h1>
        <p class="page-description">
          MCP 插件管理：filesystem / git / docker / browser，默认只读，危险操作需确认。
        </p>
      </div>
      <el-tag type="info" effect="dark">
        {{ enabledCount }} / {{ plugins.length }} enabled
      </el-tag>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <PluginTable
            :plugins="plugins"
            :loading="loading"
            :selected-plugin-id="selectedPlugin?.pluginId ?? null"
            @select="selectedPlugin = $event"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <PluginDetail
          :plugin="selectedPlugin"
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
