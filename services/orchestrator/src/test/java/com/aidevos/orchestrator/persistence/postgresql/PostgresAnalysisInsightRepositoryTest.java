package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums;
import com.aidevos.orchestrator.analysis.AnalysisInsightSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class PostgresAnalysisInsightRepositoryTest {
	@Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
	private PostgresJdbc jdbc; private ObjectMapper mapper;
	@BeforeEach void setUp() { PGSimpleDataSource source=new PGSimpleDataSource(); source.setUrl(POSTGRES.getJdbcUrl());
		source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword()); mapper=new ObjectMapper();
		new PostgresDocumentStore(source,mapper); jdbc=new PostgresJdbc(source); jdbc.update("DELETE FROM analysis_insight_sets"); }
	@Test void jsonbRoundTripQueriesStatusAndSurvivesRepositoryRestart() {
		PostgresAnalysisInsightRepository first=new PostgresAnalysisInsightRepository(jdbc,mapper); first.save(value("a1",AnalysisEnums.Status.RUNNING));
		PostgresAnalysisInsightRepository restarted=new PostgresAnalysisInsightRepository(jdbc,new ObjectMapper());
		assertEquals("a1",restarted.get("a1").analysisId()); assertEquals("a1",restarted.findByTaskId("t").analysisId());
		assertEquals(1,restarted.findByProjectId("p").size()); assertEquals(1,restarted.findByStatus(AnalysisEnums.Status.RUNNING).size());
		assertEquals(1,jdbc.queryOne("SELECT COUNT(*) FROM analysis_insight_sets WHERE jsonb_typeof(payload)='object'",r->r.getInt(1)).intValue());
	}
	@Test void duplicateSourceConstraintPreventsSecondAggregate() {
		PostgresAnalysisInsightRepository repository=new PostgresAnalysisInsightRepository(jdbc,mapper); repository.save(value("a1",AnalysisEnums.Status.PENDING));
		assertThrows(IllegalStateException.class,()->repository.save(value("a2",AnalysisEnums.Status.PENDING)));
		assertEquals(1,jdbc.queryOne("SELECT COUNT(*) FROM analysis_insight_sets",r->r.getInt(1)).intValue());
	}
	@Test void statusUpdatePersists() { PostgresAnalysisInsightRepository repository=new PostgresAnalysisInsightRepository(jdbc,mapper);
		AnalysisInsightSet value=value("a1",AnalysisEnums.Status.PENDING); repository.save(value);
		repository.save(value.withStatus(AnalysisEnums.Status.FAILED,"INTERRUPTED","retry",Instant.now()));
		assertEquals(AnalysisEnums.Status.FAILED,repository.get("a1").status()); }
	private AnalysisInsightSet value(String id,AnalysisEnums.Status status) { Instant now=Instant.now(); return new AnalysisInsightSet(id,"t","e","p","w",List.of(),
		AnalysisEnums.ExtractorType.STRUCTURED,"v1","1.0",status,null,null,null,List.of(),List.of(),now,now); }
}
