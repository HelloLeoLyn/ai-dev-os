package com.aidevos.orchestrator.analysis;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.backlog.BacklogItem;
import com.aidevos.orchestrator.backlog.BacklogPriority;
import com.aidevos.orchestrator.backlog.BacklogService;
import com.aidevos.orchestrator.backlog.BacklogSourceType;
import com.aidevos.orchestrator.backlog.BacklogStatus;
import com.aidevos.orchestrator.backlog.BacklogRecommendationContext;
import com.aidevos.orchestrator.backlog.CreateBacklogRequest;
import com.aidevos.orchestrator.outbox.OutboxTransactions;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
	private final AnalysisInsightRepository insights;
	private final RecommendationDecisionRepository decisions;
	private final BacklogService backlog;
	private final OutboxTransactions transactions;
	private final AuditService audit;
	private final Clock clock;
	@Autowired
	public RecommendationService(AnalysisInsightRepository insights,
			RecommendationDecisionRepository decisions, BacklogService backlog,
			OutboxTransactions transactions, AuditService audit) {
		this(insights,decisions,backlog,transactions,audit,Clock.systemUTC());
	}
	RecommendationService(AnalysisInsightRepository insights,
			RecommendationDecisionRepository decisions, BacklogService backlog,
			OutboxTransactions transactions, AuditService audit, Clock clock) {
		this.insights=insights;this.decisions=decisions;this.backlog=backlog;
		this.transactions=transactions;this.audit=audit;this.clock=clock;
	}

	public RecommendationView get(String id) { Source source=source(id); return view(source,decisions.get(id)); }

	public synchronized RecommendationView view(String id,String actor) {
		return transition(id,actor,RecommendationStatus.VIEWED,null,null);
	}
	public synchronized RecommendationView defer(String id,Instant until,String reason,String actor) {
		return transition(id,actor,RecommendationStatus.DEFERRED,until,normalize(reason));
	}
	public synchronized RecommendationView ignore(String id,String reason,String actor) {
		return transition(id,actor,RecommendationStatus.IGNORED,null,normalize(reason));
	}

	private RecommendationView transition(String id,String actor,RecommendationStatus requested,
			Instant until,String reason) {
		Source source=source(id);
		return transactions.execute(() -> {
			RecommendationDecision current=locked(source);
			RecommendationStatus next=next(current.status(),requested);
			if(next==current.status()) return view(source,current);
			Instant now=clock.instant();
			RecommendationDecision changed=switch(next) {
				case VIEWED -> current.transition(next,null,null,null,null,now);
				case DEFERRED -> current.transition(next,until,reason,null,null,now);
				case IGNORED -> current.transition(next,null,null,reason,null,now);
				default -> throw new IllegalStateException("Unsupported recommendation transition");
			};
			save(changed,current.version());
			audit(source,changed,event(next),actor,null);
			return view(source,changed);
		});
	}

	public synchronized RecommendationWorkItemResult createWorkItem(String id,
			CreateRecommendationWorkItemRequest request) {
		Source source=source(id);
		return transactions.execute(() -> {
			RecommendationDecision current=locked(source);
			if(current.status()==RecommendationStatus.IGNORED)
				throw new IllegalStateException("Ignored recommendation cannot create a WorkItem");
			String backlogId=stableBacklogId(id);
			BacklogItem existing=current.convertedBacklogItemId()==null ? null
				: existing(current.convertedBacklogItemId());
			if(existing==null) existing=existing(backlogId);
			if(current.status()==RecommendationStatus.WORKITEM_CREATED && existing!=null)
				return new RecommendationWorkItemResult(false,existing);
			boolean created=existing==null;
			BacklogItem item=created ? backlog.createRecommendationCandidate(backlogId,
				backlogRequest(source,request),backlogContext(source)) : existing;
			RecommendationDecision changed=current.transition(RecommendationStatus.WORKITEM_CREATED,
				null,null,null,item.getBacklogItemId(),clock.instant());
			save(changed,current.version());
			audit(source,changed,EventType.RECOMMENDATION_WORKITEM_CREATED,
				request==null?null:request.actor(),item.getBacklogItemId());
			return new RecommendationWorkItemResult(created,item);
		});
	}

	private RecommendationDecision locked(Source source) {
		Instant now=clock.instant();
		decisions.createIfAbsent(new RecommendationDecision(source.recommendation.recommendationId(),
			source.insight.analysisId(),source.insight.sourceTaskId(),source.insight.projectId(),
			RecommendationStatus.NEW,null,null,null,null,0,now,now));
		RecommendationDecision value=decisions.lock(source.recommendation.recommendationId());
		if(value==null) throw new IllegalStateException("Recommendation decision could not be locked");
		return value;
	}
	private void save(RecommendationDecision value,long expected) {
		if(!decisions.saveIfVersion(value,expected))
			throw new IllegalStateException("Recommendation was concurrently updated; retry safely");
	}
	private RecommendationStatus next(RecommendationStatus current,RecommendationStatus requested) {
		if(current==RecommendationStatus.IGNORED || current==RecommendationStatus.WORKITEM_CREATED) return current;
		return switch(requested) {
			case VIEWED -> current==RecommendationStatus.NEW || current==RecommendationStatus.DEFERRED
				? RecommendationStatus.VIEWED : current;
			case DEFERRED -> current==RecommendationStatus.NEW || current==RecommendationStatus.VIEWED
				? RecommendationStatus.DEFERRED : current;
			case IGNORED -> RecommendationStatus.IGNORED;
			default -> current;
		};
	}
	private CreateBacklogRequest backlogRequest(Source source,CreateRecommendationWorkItemRequest override) {
		RecommendedNextAction action=source.recommendation.recommendedNextAction();
		String title=text(override==null?null:override.title(),action.title());
		String base=text(override==null?null:override.description(),action.description());
		String description=description(base,action,source.recommendation);
		BacklogPriority priority=override!=null && override.priority()!=null ? override.priority()
			: BacklogPriority.valueOf(source.recommendation.priority().name());
		return new CreateBacklogRequest(title,description,BacklogStatus.IDEA,priority,
			source.insight.projectId(),source.insight.workspaceId(),BacklogSourceType.TASK,
			"recommendation:"+source.recommendation.recommendationId(),null,List.of(),tags(source));
	}
	private BacklogRecommendationContext backlogContext(Source source) {
		RecommendedNextAction action=source.recommendation.recommendedNextAction();
		return new BacklogRecommendationContext(source.recommendation.recommendationId(),
			source.insight.analysisId(),source.insight.sourceTaskId(),action.goal(),
			action.acceptanceCriteria(),source.recommendation.risk(),source.recommendation.scope(),
			source.recommendation.suggestedExecutionMode(),source.recommendation.approvalRequired());
	}
	private String description(String base,RecommendedNextAction action,Recommendation recommendation) {
		StringBuilder value=new StringBuilder(base).append("\n\nGoal:\n").append(action.goal());
		value.append("\n\nAcceptance Criteria:"); action.acceptanceCriteria().forEach(v->value.append("\n- ").append(v));
		if(!recommendation.dependencies().isEmpty()) { value.append("\n\nDependencies:");
			recommendation.dependencies().forEach(v->value.append("\n- ").append(v)); }
		return value.append("\n\nSuggested Execution Mode: ").append(recommendation.suggestedExecutionMode())
			.append("\nApproval Required: ").append(recommendation.approvalRequired()).toString();
	}
	private List<String> tags(Source source) { LinkedHashSet<String> tags=new LinkedHashSet<>();
		tags.add("recommendation"); tags.add("risk:"+source.recommendation.risk().name().toLowerCase());
		source.findings.forEach(f->tags.add("finding:"+f.category().toLowerCase())); return List.copyOf(tags); }
	private BacklogItem existing(String id) { try{return backlog.get(id);}
		catch(ResourceNotFoundException ignored){return null;} }
	private String stableBacklogId(String id) { return "backlog-"+UUID.nameUUIDFromBytes(
		("recommendation:"+id).getBytes(StandardCharsets.UTF_8)); }
	private Source source(String id) { if(id==null||id.isBlank())throw new IllegalArgumentException("recommendationId is required");
		AnalysisInsightSet insight=insights.findByRecommendationId(id); if(insight==null)throw new IllegalArgumentException("Recommendation not found: "+id);
		Recommendation recommendation=insight.recommendations().stream().filter(r->id.equals(r.recommendationId())).findFirst().orElseThrow();
		List<Finding> findings=insight.findings().stream().filter(f->recommendation.findingIds().contains(f.findingId())).toList();
		return new Source(insight,recommendation,findings); }
	private RecommendationView view(Source source,RecommendationDecision decision) { return new RecommendationView(
		source.recommendation.recommendationId(),source.insight.sourceTaskId(),source.insight.sourceExecutionRecordId(),
		source.recommendation.title(),source.recommendation.rationale(),source.recommendation.priority(),source.recommendation.risk(),
		source.recommendation.benefit(),source.recommendation.confidence(),source.recommendation.scope(),source.recommendation.dependencies(),
		source.recommendation.suggestedExecutionMode(),source.recommendation.approvalRequired(),source.findings,
		source.recommendation.evidenceRefs(),source.recommendation.recommendedNextAction(),
		decision==null?RecommendationStatus.NEW:decision.status(),decision==null?null:decision.deferUntil(),
		decision==null?null:decision.deferReason(),decision==null?null:decision.ignoreReason(),
		decision==null?null:decision.convertedBacklogItemId(),decision==null?source.insight.updatedAt():decision.updatedAt()); }
	private void audit(Source source,RecommendationDecision decision,EventType type,String actor,String backlogId) {
		Map<String,Object> metadata=new java.util.LinkedHashMap<>();metadata.put("recommendationId",decision.recommendationId());
		metadata.put("sourceTaskId",source.insight.sourceTaskId());metadata.put("projectId",source.insight.projectId());
		if(backlogId!=null)metadata.put("backlogItemId",backlogId);if(decision.deferReason()!=null)metadata.put("reason",decision.deferReason());
		if(decision.ignoreReason()!=null)metadata.put("reason",decision.ignoreReason());
		audit.adminEvent(type,"recommendation",decision.recommendationId(),actor,type.name(),Map.copyOf(metadata)); }
	private EventType event(RecommendationStatus status){return switch(status){case VIEWED->EventType.RECOMMENDATION_VIEWED;
		case DEFERRED->EventType.RECOMMENDATION_DEFERRED;case IGNORED->EventType.RECOMMENDATION_IGNORED;default->throw new IllegalArgumentException();};}
	private String normalize(String value){return value==null||value.isBlank()?null:value.trim();}
	private String text(String override,String fallback){String value=normalize(override);return value==null?fallback:value;}
	private record Source(AnalysisInsightSet insight,Recommendation recommendation,List<Finding> findings){}
}
