package com.aidevos.orchestrator.persistence.postgresql;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresPersistenceConfiguration {
	@Bean
	DataSource persistenceDataSource(
			@Value("${aidevos.persistence.postgresql.url}") String url,
			@Value("${aidevos.persistence.postgresql.username}") String username,
			@Value("${aidevos.persistence.postgresql.password}") String password) {
		PGSimpleDataSource source = new PGSimpleDataSource();
		source.setUrl(url); source.setUser(username); source.setPassword(password);
		return source;
	}
}
