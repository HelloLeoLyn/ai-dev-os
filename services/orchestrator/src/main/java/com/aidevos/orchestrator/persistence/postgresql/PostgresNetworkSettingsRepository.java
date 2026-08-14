package com.aidevos.orchestrator.persistence.postgresql;
import com.aidevos.orchestrator.network.*;
import javax.sql.DataSource;
import tools.jackson.databind.ObjectMapper;
final class PostgresNetworkSettingsRepository implements NetworkSettingsRepository {
	private static final String TYPE="network-proxy-settings"; private volatile PostgresDocumentStore store;private final DataSource source;private final ObjectMapper mapper;
	PostgresNetworkSettingsRepository(PostgresDocumentStore store){this.store=store;source=null;mapper=null;}
	PostgresNetworkSettingsRepository(DataSource source,ObjectMapper mapper){this.source=source;this.mapper=mapper;}
	private PostgresDocumentStore store(){PostgresDocumentStore current=store;if(current==null)synchronized(this){if(store==null)store=new PostgresDocumentStore(source,mapper);current=store;}return current;}
	public ProxySettings get(){return store().get(TYPE,ProxySettings.SETTINGS_ID,ProxySettings.class);}
	public void save(ProxySettings settings){store().put(TYPE,ProxySettings.SETTINGS_ID,settings,null);}
}
