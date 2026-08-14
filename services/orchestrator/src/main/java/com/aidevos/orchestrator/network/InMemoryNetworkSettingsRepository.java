package com.aidevos.orchestrator.network;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="in-memory",matchIfMissing=true)
public class InMemoryNetworkSettingsRepository implements NetworkSettingsRepository {
	private volatile ProxySettings value;
	public ProxySettings get(){return value;} public void save(ProxySettings settings){value=settings;}
}
