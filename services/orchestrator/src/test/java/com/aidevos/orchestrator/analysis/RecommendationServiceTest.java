package com.aidevos.orchestrator.analysis;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.aidevos.orchestrator.analysis.AnalysisEnums.EstimatedComplexity;
import com.aidevos.orchestrator.analysis.AnalysisEnums.ExtractorType;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Level;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Status;
import com.aidevos.orchestrator.audit.AuditRepository;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.backlog.BacklogItem;
import com.aidevos.orchestrator.backlog.BacklogPriority;
import com.aidevos.orchestrator.backlog.BacklogService;
import com.aidevos.orchestrator.backlog.BacklogSourceType;
import com.aidevos.orchestrator.backlog.BacklogStatus;
import com.aidevos.orchestrator.backlog.InMemoryBacklogRepository;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectTaskService;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationServiceTest {
	private static final Instant NOW=Instant.parse("2026-08-15T00:00:00Z");
	private InMemoryAnalysisInsightRepository insights;
	private InMemoryRecommendationDecisionRepository decisions;
	private InMemoryBacklogRepository backlogs;
	private ProjectTaskService projectTasks;
	private TaskCenterService taskCenter;
	private InMemoryAuditRepository events;
	private RecommendationService service;
	@BeforeEach void setUp(){insights=new InMemoryAnalysisInsightRepository();insights.save(insight());
		decisions=new InMemoryRecommendationDecisionRepository();backlogs=new InMemoryBacklogRepository();
		projectTasks=mock(ProjectTaskService.class);taskCenter=mock(TaskCenterService.class);events=new InMemoryAuditRepository();
		service=service(decisions,new AuditService(events));}

	@Test void getHasNoSideEffectAndViewTransitionsOnlyExplicitly(){
		assertEquals(RecommendationStatus.NEW,service.get("r1").status()); assertNull(decisions.get("r1"));
		assertEquals(RecommendationStatus.VIEWED,service.view("r1","alice").status());
		assertEquals(EventType.RECOMMENDATION_VIEWED,lastEvent().type());
	}
	@Test void deferAndExplicitViewReactivate(){service.defer("r1",NOW.plusSeconds(3600),"later","alice");
		assertEquals(RecommendationStatus.DEFERRED,service.get("r1").status());
		assertEquals(RecommendationStatus.DEFERRED,service.get("r1").status());
		assertEquals(RecommendationStatus.VIEWED,service.view("r1","alice").status());}
	@Test void viewedCanDeferAndAllActiveStatesCanIgnore(){service.view("r1",null);
		assertEquals(RecommendationStatus.DEFERRED,service.defer("r1",null,null,null).status());
		assertEquals(RecommendationStatus.IGNORED,service.ignore("r1","not now",null).status());
		assertEquals(RecommendationStatus.IGNORED,service.view("r1",null).status());}
	@Test void ignoredCannotCreateWorkItem(){service.ignore("r1",null,null);
		assertThrows(IllegalStateException.class,()->service.createWorkItem("r1",null)); assertTrue(backlogs.list().isEmpty());}
	@Test void createsExistingBacklogIdeaWithDefaultMappingAndNoTaskPlanOrExecution(){
		RecommendationWorkItemResult result=service.createWorkItem("r1",null); BacklogItem item=result.backlogItem();
		assertTrue(result.created());assertEquals(BacklogStatus.IDEA,item.getStatus());assertEquals("project-1",item.getProjectId());
		assertEquals("workspace-1",item.getWorkspaceId());assertEquals("Action title",item.getTitle());
		assertEquals(BacklogPriority.HIGH,item.getPriority());assertEquals(BacklogSourceType.TASK,item.getSourceType());
		assertEquals("recommendation:r1",item.getSourceReference());assertTrue(item.getDescription().contains("Suggested Execution Mode: READ_WRITE"));
		assertTrue(item.getDescription().contains("Dependency text"));assertTrue(item.getDependsOn().isEmpty());
		assertNotNull(item.getRecommendationContext());
		assertEquals("r1",item.getRecommendationContext().recommendationId());
		assertEquals("analysis-1",item.getRecommendationContext().analysisId());
		assertEquals("task-1",item.getRecommendationContext().sourceTaskId());
		assertEquals("Goal",item.getRecommendationContext().goal());
		assertEquals(List.of("Done"),item.getRecommendationContext().acceptanceCriteria());
		assertEquals(Level.MEDIUM,item.getRecommendationContext().risk());
		assertEquals(List.of("src"),item.getRecommendationContext().scope());
		assertEquals(ExecutionMode.READ_WRITE,item.getRecommendationContext().suggestedExecutionMode());
		assertTrue(item.getRecommendationContext().approvalRequired());
		verifyNoInteractions(projectTasks,taskCenter);
	}
	@Test void userOverridesTitleDescriptionAndPriority(){BacklogItem item=service.createWorkItem("r1",
		new CreateRecommendationWorkItemRequest("Override","Custom",BacklogPriority.LOW,"alice")).backlogItem();
		assertEquals("Override",item.getTitle());assertTrue(item.getDescription().startsWith("Custom"));assertEquals(BacklogPriority.LOW,item.getPriority());}
	@Test void repeatedAndConcurrentRequestsCreateOneStableBacklog()throws Exception{
		RecommendationWorkItemResult first=service.createWorkItem("r1",null), second=service.createWorkItem("r1",null);
		assertTrue(first.created());assertFalse(second.created());assertEquals(first.backlogItem().getBacklogItemId(),second.backlogItem().getBacklogItemId());
		assertEquals(1,backlogs.list().size());
		try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
			List<Callable<RecommendationWorkItemResult>> calls=java.util.stream.IntStream.range(0,10)
				.mapToObj(i->(Callable<RecommendationWorkItemResult>)()->service.createWorkItem("r1",null)).toList();
			for(var future:executor.invokeAll(calls)) assertNotNull(future.get());}
		assertEquals(1,backlogs.list().size());
	}
	@Test void workItemTerminalDoesNotRegress(){service.createWorkItem("r1",null);
		assertEquals(RecommendationStatus.WORKITEM_CREATED,service.view("r1",null).status());
		assertEquals(RecommendationStatus.WORKITEM_CREATED,service.defer("r1",null,null,null).status());
		assertEquals(RecommendationStatus.WORKITEM_CREATED,service.ignore("r1",null,null).status());}
	@Test void retryRepairsBacklinkAfterDecisionUpdateFailureWithoutDuplicate(){
		FailOnceDecisionRepository failing=new FailOnceDecisionRepository(decisions);
		RecommendationService unstable=service(failing,new AuditService(events));
		assertThrows(IllegalStateException.class,()->unstable.createWorkItem("r1",null));assertEquals(1,backlogs.list().size());
		RecommendationWorkItemResult recovered=unstable.createWorkItem("r1",null);
		assertFalse(recovered.created());assertEquals(1,backlogs.list().size());assertEquals(recovered.backlogItem().getBacklogItemId(),decisions.get("r1").convertedBacklogItemId());
	}
	@Test void auditFailureDoesNotCauseDuplicateBacklog(){AuditRepository down=new AuditRepository(){
		public EventRecord append(EventRecord e){throw new IllegalStateException("down");}public EventRecord get(String id){return null;}public List<EventRecord> query(EventQuery q){return List.of();}};
		RecommendationService value=service(decisions,new AuditService(down));
		RecommendationWorkItemResult first=value.createWorkItem("r1",null),second=value.createWorkItem("r1",null);
		assertTrue(first.created());assertFalse(second.created());assertEquals(1,backlogs.list().size());}

	private RecommendationService service(RecommendationDecisionRepository repository,AuditService audit){
		ProjectService projects=mock(ProjectService.class);when(projects.getProject("project-1")).thenReturn(Optional.of(mock(Project.class)));
		WorkspaceService workspaces=mock(WorkspaceService.class);when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(
			new Workspace("workspace-1","project-1","/tmp","main",WorkspaceStatus.READY,NOW,NOW)));
		BacklogService backlog=new BacklogService(backlogs,projects,workspaces,projectTasks,taskCenter,audit);
		return new RecommendationService(insights,repository,backlog,com.aidevos.orchestrator.outbox.OutboxTransactions.passThrough(),audit,
			Clock.fixed(NOW,ZoneOffset.UTC));}
	private EventRecord lastEvent(){return events.query(EventQuery.all()).getLast();}
	private AnalysisInsightSet insight(){Finding finding=new Finding("f1","Finding","Summary","QUALITY",Level.HIGH,.9,List.of("src"),List.of());
		RecommendedNextAction action=new RecommendedNextAction("a1","Action title","Action description","Goal",List.of("Done"),List.of("src"),List.of(),
			ExecutionMode.READ_WRITE,false,EstimatedComplexity.SMALL);
		Recommendation recommendation=new Recommendation("r1",List.of("f1"),"Recommendation","Rationale",Level.HIGH,Level.MEDIUM,Level.HIGH,
			List.of("src"),List.of("Dependency text"),ExecutionMode.READ_WRITE,false,List.of(),.8,action);
		return new AnalysisInsightSet("analysis-1","task-1","execution-1","project-1","workspace-1",List.of(),ExtractorType.STRUCTURED,"v1","1.0",
			Status.SUCCEEDED,null,null,"hash",List.of(finding),List.of(recommendation),NOW,NOW);}
	private static class FailOnceDecisionRepository implements RecommendationDecisionRepository{
		private final RecommendationDecisionRepository delegate;private boolean failed;
		FailOnceDecisionRepository(RecommendationDecisionRepository value){delegate=value;}
		public RecommendationDecision get(String id){return delegate.get(id);}public RecommendationDecision createIfAbsent(RecommendationDecision v){return delegate.createIfAbsent(v);}
		public RecommendationDecision lock(String id){return delegate.lock(id);}public boolean saveIfVersion(RecommendationDecision v,long version){if(!failed){failed=true;throw new IllegalStateException("interrupted");}return delegate.saveIfVersion(v,version);}}
}
