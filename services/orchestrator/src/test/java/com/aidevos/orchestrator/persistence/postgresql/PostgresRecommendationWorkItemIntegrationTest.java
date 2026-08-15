package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import com.aidevos.orchestrator.analysis.*;
import com.aidevos.orchestrator.analysis.AnalysisEnums.*;
import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.backlog.*;
import com.aidevos.orchestrator.outbox.PostgresOutboxTransactions;
import com.aidevos.orchestrator.project.*;
import com.aidevos.orchestrator.taskcenter.*;
import com.aidevos.orchestrator.workspace.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker=true)
class PostgresRecommendationWorkItemIntegrationTest {
	@Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
	private PGSimpleDataSource dataSource;private ObjectMapper mapper;private PostgresAnalysisInsightRepository insights;
	@BeforeEach void setUp()throws Exception{dataSource=new PGSimpleDataSource();dataSource.setUrl(POSTGRES.getJdbcUrl());dataSource.setUser(POSTGRES.getUsername());dataSource.setPassword(POSTGRES.getPassword());
		mapper=new ObjectMapper();new PostgresDocumentStore(dataSource,mapper);insights=new PostgresAnalysisInsightRepository(new PostgresJdbc(dataSource),mapper);
		try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){s.execute("DELETE FROM recommendation_decisions");s.execute("DELETE FROM repository_documents WHERE repository_type='backlog-item'");s.execute("DELETE FROM analysis_insight_sets");}
		insights.save(insight());}
	@Test void twoServiceInstancesCreateAtMostOneBacklog()throws Exception{
		RecommendationService first=service(AuditService.noop()),second=service(AuditService.noop());
		try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
			var a=executor.submit(()->first.createWorkItem("r1",null));var b=executor.submit(()->second.createWorkItem("r1",null));
			var one=a.get();var two=b.get();assertEquals(one.backlogItem().getBacklogItemId(),two.backlogItem().getBacklogItemId());
			assertEquals(1,(one.created()?1:0)+(two.created()?1:0));}
		assertEquals(1,count("SELECT COUNT(*) FROM repository_documents WHERE repository_type='backlog-item'"));
		assertEquals(1,count("SELECT COUNT(*) FROM recommendation_decisions WHERE status='WORKITEM_CREATED'"));
	}
	@Test void auditFailureRollsBackThenRetryCreatesOnce(){AuditRepository down=new AuditRepository(){public EventRecord append(EventRecord e){throw new IllegalStateException("down");}
		public EventRecord get(String id){return null;}public List<EventRecord> query(EventQuery q){return List.of();}};
		assertThrows(IllegalStateException.class,()->service(new AuditService(down)).createWorkItem("r1",null));
		assertEquals(0,count("SELECT COUNT(*) FROM repository_documents WHERE repository_type='backlog-item'"));assertEquals(0,count("SELECT COUNT(*) FROM recommendation_decisions"));
		var retry=service(AuditService.noop()).createWorkItem("r1",null);assertTrue(retry.created());assertEquals(1,count("SELECT COUNT(*) FROM repository_documents WHERE repository_type='backlog-item'"));}
	private RecommendationService service(AuditService audit){var decisions=new PostgresRecommendationDecisionRepository(dataSource);var backlogRepo=new PostgresBacklogRepository(dataSource,mapper);
		ProjectService projects=mock(ProjectService.class);when(projects.getProject("project-1")).thenReturn(Optional.of(mock(Project.class)));
		WorkspaceService workspaces=mock(WorkspaceService.class);when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(new Workspace("workspace-1","project-1","/tmp","main",WorkspaceStatus.READY,Instant.now(),Instant.now())));
		BacklogService backlog=new BacklogService(backlogRepo,projects,workspaces,mock(ProjectTaskService.class),mock(TaskCenterService.class),audit);
		return new RecommendationService(insights,decisions,backlog,new PostgresOutboxTransactions(dataSource),audit);}
	private int count(String sql){try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getInt(1);}catch(Exception e){throw new IllegalStateException(e);}}
	private AnalysisInsightSet insight(){Instant now=Instant.now();Finding finding=new Finding("f1","Finding","Summary","QUALITY",Level.HIGH,.9,List.of(),List.of());
		RecommendedNextAction action=new RecommendedNextAction("a1","Act","Description","Goal",List.of("Done"),List.of(),List.of(),ExecutionMode.READ_ONLY,false,EstimatedComplexity.SMALL);
		Recommendation recommendation=new Recommendation("r1",List.of("f1"),"Recommendation","Why",Level.HIGH,Level.MEDIUM,Level.HIGH,List.of(),List.of(),ExecutionMode.READ_ONLY,false,List.of(),.8,action);
		return new AnalysisInsightSet("a1","t1","e1","project-1","workspace-1",List.of(),ExtractorType.STRUCTURED,"v1","1.0",Status.SUCCEEDED,null,null,"hash",List.of(finding),List.of(recommendation),now,now);}
}
