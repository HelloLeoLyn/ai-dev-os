<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AsyncState from '../components/AsyncState.vue'
import ConsoleCard from '../components/ConsoleCard.vue'
import SectionHeader from '../components/SectionHeader.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { networkApi, type NetworkProbe, type ProxySettings } from '../api/network'

const loading=ref(true), saving=ref(false), probing=ref(false), errorMessage=ref(''), probes=ref<NetworkProbe[]>([])
const form=reactive<ProxySettings>({mode:'SYSTEM',hostStrategy:'MANUAL',httpProxy:'',httpsProxy:'',socks5Proxy:'',noProxy:''})
function assign(value:ProxySettings){Object.assign(form,value)}
async function load(){loading.value=true;errorMessage.value='';try{assign(await networkApi.get())}catch(e){errorMessage.value=e instanceof Error?e.message:String(e)}finally{loading.value=false}}
async function save(){saving.value=true;errorMessage.value='';try{assign(await networkApi.save({...form}))}catch(e){errorMessage.value=e instanceof Error?e.message:String(e)}finally{saving.value=false}}
async function runProbe(){probing.value=true;errorMessage.value='';try{probes.value=await networkApi.probe()}catch(e){errorMessage.value=e instanceof Error?e.message:String(e)}finally{probing.value=false}}
onMounted(load)
</script>

<template>
  <section class="network-settings">
    <SectionHeader eyebrow="Runtime Settings" title="Network / Proxy" description="Configure version-aware proxy routing for controlled commands and HTTP clients.">
      <el-button :loading="probing" @click="runProbe">Run Network Probe</el-button>
      <el-button type="primary" :loading="saving" @click="save">Save</el-button>
    </SectionHeader>
    <AsyncState :loading="loading" :error="errorMessage" :empty="false" @retry="load">
      <ConsoleCard title="Proxy Configuration">
        <el-form label-position="top">
          <el-form-item label="Mode"><el-radio-group v-model="form.mode"><el-radio-button value="DIRECT">DIRECT</el-radio-button><el-radio-button value="SYSTEM">SYSTEM</el-radio-button><el-radio-button value="CUSTOM">CUSTOM</el-radio-button></el-radio-group></el-form-item>
          <template v-if="form.mode==='CUSTOM'">
            <el-form-item label="Host Strategy"><el-radio-group v-model="form.hostStrategy"><el-radio value="MANUAL">MANUAL</el-radio><el-radio value="AUTO_WINDOWS_HOST">AUTO_WINDOWS_HOST</el-radio></el-radio-group></el-form-item>
            <p v-if="form.hostStrategy==='AUTO_WINDOWS_HOST'" class="resolved-host"><span>Resolved Windows Host</span><strong>{{form.resolvedWindowsHost||form.errorCode||'Resolving…'}}</strong></p>
            <el-form-item label="HTTP Proxy"><el-input v-model="form.httpProxy" type="password" autocomplete="new-password" placeholder="http://user:password@host:port" /></el-form-item>
            <el-form-item label="HTTPS Proxy"><el-input v-model="form.httpsProxy" type="password" autocomplete="new-password" placeholder="http://user:password@host:port" /></el-form-item>
            <el-form-item label="SOCKS5 Proxy"><el-input v-model="form.socks5Proxy" type="password" autocomplete="new-password" placeholder="socks5://user:password@host:port" /></el-form-item>
          </template>
          <el-form-item label="NO_PROXY"><el-input v-model="form.noProxy" type="textarea" placeholder="internal.example.com,.corp" /><small>localhost, 127.0.0.1 and ::1 are always DIRECT.</small></el-form-item>
        </el-form>
      </ConsoleCard>
      <ConsoleCard title="Network Probe" eyebrow="Effective runtime routing">
        <el-table :data="probes" empty-text="Run the probe to test current settings">
          <el-table-column prop="target" label="Target" min-width="170"/><el-table-column prop="url" label="URL" min-width="280"/>
          <el-table-column label="Route" width="120"><template #default="scope"><StatusBadge :status="scope.row.route" size="small"/></template></el-table-column>
          <el-table-column prop="durationMs" label="Duration" width="110"><template #default="scope">{{scope.row.durationMs}} ms</template></el-table-column>
          <el-table-column label="Result" min-width="180"><template #default="scope"><span :class="scope.row.success?'probe-ok':'probe-failed'">{{scope.row.success?'CONNECTED':scope.row.errorCode||'FAILED'}}</span></template></el-table-column>
        </el-table>
      </ConsoleCard>
    </AsyncState>
  </section>
</template>

<style scoped>
.network-settings{display:grid;gap:16px}.resolved-host{display:flex;gap:16px;padding:12px;background:var(--console-surface-muted,#151b26);border-radius:8px}.resolved-host span{color:var(--console-text-muted,#8f9bad)}.probe-ok{color:var(--console-success,#53d69d)}.probe-failed{color:var(--console-danger,#ff6b75)}small{color:var(--console-text-muted,#8f9bad)}
</style>
