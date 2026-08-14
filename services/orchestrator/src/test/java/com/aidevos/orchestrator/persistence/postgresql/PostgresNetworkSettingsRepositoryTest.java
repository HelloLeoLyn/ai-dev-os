package com.aidevos.orchestrator.persistence.postgresql;
import com.aidevos.orchestrator.network.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PostgresNetworkSettingsRepositoryTest {
	@Test void persistsInRepositoryDocuments(){PostgresDocumentStore store=mock(PostgresDocumentStore.class);PostgresNetworkSettingsRepository repo=new PostgresNetworkSettingsRepository(store);ProxySettings settings=new ProxySettings();settings.setVersion(7);when(store.get("network-proxy-settings",ProxySettings.SETTINGS_ID,ProxySettings.class)).thenReturn(settings);repo.save(settings);verify(store).put("network-proxy-settings",ProxySettings.SETTINGS_ID,settings,null);assertEquals(7,repo.get().getVersion());}
}
