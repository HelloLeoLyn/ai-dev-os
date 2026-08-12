<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useTaskNotifications } from '../composables/useTaskNotifications'

const center = useTaskNotifications()

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

async function enableDesktop(): Promise<void> {
  const permission = await center.enableDesktopNotifications()
  if (permission === 'granted') ElMessage.success('桌面通知已启用。')
  else if (permission === 'unsupported') ElMessage.warning('当前浏览器不支持桌面通知。')
  else ElMessage.info('未启用桌面通知，站内通知仍会正常工作。')
}

function openPrimary(item: { id: string; taskId: string; type: string }): void {
  center.markRead(item.id)
  center.centerVisible.value = false
  if (item.type === 'rejected') void center.openTask(item.taskId)
  else void center.openExecution(item.taskId)
}

function openTimeline(item: { id: string; taskId: string }): void {
  center.markRead(item.id)
  center.centerVisible.value = false
  void center.openTimeline(item.taskId)
}
</script>

<template>
  <div class="notification-entry">
    <el-badge :value="center.unreadCount.value" :hidden="center.unreadCount.value === 0" :max="99">
      <el-button aria-label="Task notifications" @click="center.centerVisible.value = true">
        通知
      </el-button>
    </el-badge>
  </div>

  <el-drawer v-model="center.centerVisible.value" title="Task Notifications" size="min(520px, 94vw)" append-to-body>
    <div class="notification-toolbar">
      <el-button size="small" @click="enableDesktop">启用桌面通知</el-button>
      <el-button size="small" text :disabled="center.unreadCount.value === 0" @click="center.markAllRead">全部标记已读</el-button>
    </div>
    <ol v-if="center.notifications.value.length" class="notification-list">
      <li v-for="item in center.notifications.value" :key="item.id" :class="[`is-${item.type}`, { 'is-unread': !item.read }]">
        <button class="notification-body" type="button" @click="openPrimary(item)">
          <span class="notification-status">{{ item.status }}</span>
          <strong>{{ item.taskName }}</strong>
          <p>{{ item.message }}</p>
          <small>完成时间：{{ formatDate(item.completedAt) }}</small>
          <small v-if="item.artifactCount !== null">Artifacts：{{ item.artifactCount }}</small>
        </button>
        <div class="notification-actions">
          <el-button size="small" type="primary" text @click="openPrimary(item)">
            {{ item.type === 'rejected' ? '查看 Task' : '查看 Execution' }}
          </el-button>
          <el-button size="small" text @click="openTimeline(item)">查看 Timeline</el-button>
          <el-button v-if="!item.read" size="small" text @click="center.markRead(item.id)">标记已读</el-button>
        </div>
      </li>
    </ol>
    <el-empty v-else description="当前 session 暂无 Task 通知" />
  </el-drawer>
</template>

<style scoped>
.notification-entry { display: flex; align-items: center; }
.notification-toolbar { display: flex; justify-content: space-between; gap: .75rem; margin-bottom: 1rem; }
.notification-list { display: grid; gap: .75rem; margin: 0; padding: 0; list-style: none; }
.notification-list li { border: 1px solid var(--color-border); border-left: 3px solid var(--color-text-muted); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }
.notification-list li.is-success { border-left-color: var(--color-success); }
.notification-list li.is-failure { border-left-color: var(--color-danger); }
.notification-list li.is-unread { background: rgb(124 156 255 / 9%); }
.notification-body { display: grid; width: 100%; gap: .35rem; padding: 1rem; border: 0; color: inherit; background: transparent; cursor: pointer; text-align: left; }
.notification-body p { margin: .2rem 0; color: var(--color-text-muted); white-space: pre-wrap; overflow-wrap: anywhere; }
.notification-body small { color: var(--color-text-muted); }
.notification-status { color: var(--color-primary-strong); font-size: .72rem; font-weight: 800; letter-spacing: .08em; }
.notification-actions { display: flex; flex-wrap: wrap; padding: 0 .5rem .5rem; }
</style>
