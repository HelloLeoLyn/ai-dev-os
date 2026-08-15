package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import com.aidevos.orchestrator.analysis.RecommendationDecision;
import com.aidevos.orchestrator.analysis.RecommendationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class PostgresRecommendationDecisionRepositoryTest {
	@Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
	private PGSimpleDataSource dataSource;
	@BeforeEach void setUp()throws Exception{dataSource=new PGSimpleDataSource();dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());dataSource.setPassword(POSTGRES.getPassword());new PostgresDocumentStore(dataSource,new ObjectMapper());
		try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){s.execute("DELETE FROM recommendation_decisions");}}
	@Test void persistsStateBacklinkAndRestart(){var first=new PostgresRecommendationDecisionRepository(dataSource);Instant now=Instant.now();
		RecommendationDecision initial=value(now);first.createIfAbsent(initial);RecommendationDecision changed=initial.transition(
			RecommendationStatus.WORKITEM_CREATED,null,null,null,"backlog-1",now.plusSeconds(1));
		assertTrue(first.saveIfVersion(changed,0));var restarted=new PostgresRecommendationDecisionRepository(dataSource);
		assertEquals(RecommendationStatus.WORKITEM_CREATED,restarted.get("r1").status());assertEquals("backlog-1",restarted.get("r1").convertedBacklogItemId());}
	@Test void createAndCasAreIdempotent(){var repository=new PostgresRecommendationDecisionRepository(dataSource);Instant now=Instant.now();
		assertEquals(0,repository.createIfAbsent(value(now)).version());assertEquals(0,repository.createIfAbsent(value(now.plusSeconds(2))).version());
		RecommendationDecision changed=value(now).transition(RecommendationStatus.VIEWED,null,null,null,null,now);
		assertTrue(repository.saveIfVersion(changed,0));assertFalse(repository.saveIfVersion(changed,0));}
	@Test void uniqueBacklogBacklinkIsEnforced(){var repository=new PostgresRecommendationDecisionRepository(dataSource);Instant now=Instant.now();
		repository.createIfAbsent(value(now));RecommendationDecision other=new RecommendationDecision("r2","a","t","p",RecommendationStatus.NEW,null,null,null,null,0,now,now);
		repository.createIfAbsent(other);assertTrue(repository.saveIfVersion(value(now).transition(RecommendationStatus.WORKITEM_CREATED,null,null,null,"backlog-1",now),0));
		assertThrows(IllegalStateException.class,()->repository.saveIfVersion(other.transition(RecommendationStatus.WORKITEM_CREATED,null,null,null,"backlog-1",now),0));}
	private RecommendationDecision value(Instant now){return new RecommendationDecision("r1","a","t","p",RecommendationStatus.NEW,null,null,null,null,0,now,now);}
}
