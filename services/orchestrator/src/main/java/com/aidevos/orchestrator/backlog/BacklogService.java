package com.aidevos.orchestrator.backlog;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectTaskService;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BacklogService {
	private static final Map<BacklogStatus, Set<BacklogStatus>> TRANSITIONS = transitions();
	private final BacklogRepository repository;
	private final ProjectService projects;
	private final WorkspaceService workspaces;
	private final ProjectTaskService projectTasks;
	private final TaskCenterService taskCenter;
	private final AuditService audit;
	private final Clock clock;

	@Autowired
	public BacklogService(BacklogRepository repository, ProjectService projects,
			WorkspaceService workspaces, ProjectTaskService projectTasks,
			TaskCenterService taskCenter, AuditService audit) {
		this(repository, projects, workspaces, projectTasks, taskCenter, audit, Clock.systemUTC());
	}

	BacklogService(BacklogRepository repository, ProjectService projects,
			WorkspaceService workspaces, ProjectTaskService projectTasks,
			TaskCenterService taskCenter, AuditService audit, Clock clock) {
		this.repository = repository;
		this.projects = projects;
		this.workspaces = workspaces;
		this.projectTasks = projectTasks;
		this.taskCenter = taskCenter;
		this.audit = audit;
		this.clock = clock;
	}

	public synchronized BacklogItem create(CreateBacklogRequest request) {
		return createWithId("backlog-" + UUID.randomUUID(), request, false);
	}

	public synchronized BacklogItem createRecommendationCandidate(String stableId,
			CreateBacklogRequest request) {
		BacklogItem existing=repository.get(required(stableId,"Stable backlog id is required"));
		if(existing!=null) return existing;
		return createWithId(stableId, request, true);
	}

	private BacklogItem createWithId(String id, CreateBacklogRequest request,
			boolean forceIdea) {
		require(request != null, "Backlog request is required");
		String title = required(request.title(), "title is required");
		BacklogStatus status = forceIdea ? BacklogStatus.IDEA
			: request.status() == null ? BacklogStatus.IDEA : request.status();
		require(status == BacklogStatus.IDEA || status == BacklogStatus.PLANNED
			|| status == BacklogStatus.READY || status == BacklogStatus.BLOCKED,
			"Initial backlog status is invalid: " + status);
		Context context = validateContext(request.projectId(), request.workspaceId());
		List<String> dependencies = dependencies(null, request.dependsOn());
		if (status == BacklogStatus.READY) requireDependenciesDone(dependencies);
		String reason = status == BacklogStatus.BLOCKED
			? required(request.blockedReason(), "blockedReason is required") : null;
		Instant now = clock.instant();
		BacklogItem item = new BacklogItem(id, title,
			normalize(request.description()), status,
			request.priority() == null ? BacklogPriority.MEDIUM : request.priority(),
			context.projectId(), context.workspaceId(),
			request.sourceType() == null ? BacklogSourceType.MANUAL : request.sourceType(),
			normalize(request.sourceReference()), dependencies, values(request.tags()), now);
		if (status == BacklogStatus.BLOCKED) item.changeStatus(status, reason, now);
		repository.save(item);
		audit.backlogEvent(EventType.BACKLOG_CREATED, item.getBacklogItemId(), null,
			null, status.name(), "Backlog item created", metadata(item));
		return item;
	}

	public synchronized BacklogItem update(String id, UpdateBacklogRequest request) {
		BacklogItem item = requireItem(id);
		require(request != null, "Backlog update is required");
		require(item.getStatus() != BacklogStatus.DONE && item.getStatus() != BacklogStatus.CANCELLED
			&& item.getStatus() != BacklogStatus.CONVERTED, "Backlog item is not editable: " + item.getStatus());
		String title = required(request.title(), "title is required");
		Context context = validateContext(request.projectId(), request.workspaceId());
		List<String> dependencies = dependencies(id, request.dependsOn());
		if (item.getStatus() == BacklogStatus.READY) requireDependenciesDone(dependencies);
		BacklogPriority priority = request.priority() == null ? BacklogPriority.MEDIUM : request.priority();
		BacklogSourceType source = request.sourceType() == null ? BacklogSourceType.MANUAL : request.sourceType();
		List<String> tags = values(request.tags());
		String description = normalize(request.description());
		String reference = normalize(request.sourceReference());
		boolean changed = !Objects.equals(item.getTitle(), title)
			|| !Objects.equals(item.getDescription(), description) || item.getPriority() != priority
			|| !Objects.equals(item.getProjectId(), context.projectId())
			|| !Objects.equals(item.getWorkspaceId(), context.workspaceId())
			|| item.getSourceType() != source || !Objects.equals(item.getSourceReference(), reference)
			|| !item.getDependsOn().equals(dependencies) || !item.getTags().equals(tags);
		if (!changed) return item;
		item.update(title, description, priority, context.projectId(), context.workspaceId(), source,
			reference, dependencies, tags, clock.instant());
		repository.save(item);
		audit.backlogEvent(EventType.BACKLOG_UPDATED, id, null, item.getStatus().name(),
			item.getStatus().name(), "Backlog item updated", metadata(item));
		return item;
	}

	public synchronized BacklogItem changeStatus(String id, ChangeBacklogStatusRequest request) {
		BacklogItem item = requireItem(id);
		require(request != null && request.status() != null, "status is required");
		BacklogStatus from = item.getStatus();
		BacklogStatus to = request.status();
		if (from == to) {
			if (to == BacklogStatus.BLOCKED) {
				String reason = required(request.blockedReason(), "blockedReason is required");
				if (!reason.equals(item.getBlockedReason())) {
					item.changeStatus(to, reason, clock.instant()); repository.save(item);
					audit.backlogEvent(EventType.BACKLOG_UPDATED, id, null, from.name(), to.name(),
						"Backlog blocked reason updated", metadata(item));
				}
			}
			return item;
		}
		require(TRANSITIONS.getOrDefault(from, Set.of()).contains(to),
			"Invalid backlog transition: " + from + " -> " + to);
		if (to == BacklogStatus.READY) requireDependenciesDone(item.getDependsOn());
		String reason = to == BacklogStatus.BLOCKED
			? required(request.blockedReason(), "blockedReason is required") : null;
		item.changeStatus(to, reason, clock.instant());
		repository.save(item);
		EventType type = transitionEvent(from, to);
		audit.backlogEvent(type, id, item.getConvertedTaskId(), from.name(), to.name(),
			"Backlog status changed to " + to, metadata(item));
		return item;
	}

	public synchronized BacklogConversionResult convertToTask(String id,
			ConvertBacklogToTaskRequest request) {
		BacklogItem item = refresh(requireItem(id));
		if (item.getConvertedTaskId() != null) {
			TaskRecord existing = taskCenter.getTask(item.getConvertedTaskId())
				.orElseThrow(() -> new IllegalStateException("Converted Task not found: " + item.getConvertedTaskId()));
			return new BacklogConversionResult(item, existing);
		}
		require(item.getStatus() == BacklogStatus.READY, "Only READY backlog items can be converted");
		require(request != null, "Task conversion request is required");
		requireDependenciesDone(item.getDependsOn());
		String projectId = required(request.projectId(), "projectId is required");
		String workspaceId = required(request.workspaceId(), "workspaceId is required");
		if (item.getProjectId() != null) require(item.getProjectId().equals(projectId),
			"Conversion projectId does not match backlog item");
		if (item.getWorkspaceId() != null) require(item.getWorkspaceId().equals(workspaceId),
			"Conversion workspaceId does not match backlog item");
		String goal = required(request.goal(), "goal is required");
		ExecutionMode mode = request.executionMode() == null ? ExecutionMode.READ_ONLY : request.executionMode();
		CreateTaskRequest taskRequest = new CreateTaskRequest(item.getTitle(), item.getDescription(), goal,
			normalize(request.plannerName()), projectId, workspaceId, mode);
		TaskRecord task = projectTasks.createTask(projectId, taskRequest);
		Instant now = clock.instant();
		item.bindContext(projectId, workspaceId, now);
		item.converted(task.getTaskId(), now);
		repository.save(item);
		audit.backlogEvent(EventType.BACKLOG_CONVERTED_TO_TASK, id, task.getTaskId(),
			BacklogStatus.READY.name(), BacklogStatus.CONVERTED.name(),
			"Backlog item converted to Task", metadata(item));
		return new BacklogConversionResult(item, task);
	}

	public BacklogItem get(String id) { return refresh(requireItem(id)); }

	public List<BacklogItem> list(BacklogStatus status, BacklogPriority priority,
			String projectId, BacklogSourceType sourceType) {
		Stream<BacklogItem> stream = (projectId == null || projectId.isBlank()
			? repository.list() : repository.listByProjectId(projectId.trim())).stream().map(this::refresh);
		if (status != null) stream = stream.filter(item -> item.getStatus() == status);
		if (priority != null) stream = stream.filter(item -> item.getPriority() == priority);
		if (sourceType != null) stream = stream.filter(item -> item.getSourceType() == sourceType);
		return stream.sorted(java.util.Comparator.comparing(BacklogItem::getUpdatedAt).reversed()).toList();
	}

	private synchronized BacklogItem refresh(BacklogItem item) {
		if (item.getStatus() != BacklogStatus.CONVERTED || item.getConvertedTaskId() == null) return item;
		TaskRecord task = taskCenter.getTask(item.getConvertedTaskId()).orElse(null);
		if (task == null || (task.getStatus() != TaskStatus.SUCCESS && task.getStatus() != TaskStatus.COMPLETED)) return item;
		item.changeStatus(BacklogStatus.DONE, null, clock.instant());
		repository.save(item);
		audit.backlogEvent(EventType.BACKLOG_COMPLETED, item.getBacklogItemId(), task.getTaskId(),
			BacklogStatus.CONVERTED.name(), BacklogStatus.DONE.name(),
			"Backlog item completed with linked Task", metadata(item));
		return item;
	}

	private List<String> dependencies(String itemId, List<String> requested) {
		List<String> result = values(requested);
		for (String dependencyId : result) {
			require(repository.get(dependencyId) != null, "Backlog dependency not found: " + dependencyId);
			require(!dependencyId.equals(itemId), "Backlog item cannot depend on itself");
			if (itemId != null) require(!reaches(dependencyId, itemId, new java.util.HashSet<>()),
				"Backlog dependency cycle detected");
		}
		return result;
	}

	private boolean reaches(String current, String target, Set<String> visited) {
		if (current.equals(target)) return true;
		if (!visited.add(current)) return false;
		BacklogItem item = repository.get(current);
		return item != null && item.getDependsOn().stream().anyMatch(next -> reaches(next, target, visited));
	}

	private void requireDependenciesDone(List<String> dependencies) {
		for (String dependencyId : dependencies) {
			BacklogItem dependency = repository.get(dependencyId);
			require(dependency != null && refresh(dependency).getStatus() == BacklogStatus.DONE,
				"Backlog dependency is not DONE: " + dependencyId);
		}
	}

	private Context validateContext(String projectId, String workspaceId) {
		String project = normalize(projectId);
		String workspace = normalize(workspaceId);
		require(workspace == null || project != null, "projectId is required when workspaceId is set");
		if (project != null) projects.getProject(project)
			.orElseThrow(() -> new ResourceNotFoundException("Project", project));
		if (workspace != null) {
			Workspace value = workspaces.getWorkspace(workspace)
				.orElseThrow(() -> new ResourceNotFoundException("Workspace", workspace));
			require(project.equals(value.getProjectId()), "Workspace does not belong to project");
		}
		return new Context(project, workspace);
	}

	private BacklogItem requireItem(String id) {
		String value = required(id, "Backlog id is required");
		BacklogItem item = repository.get(value);
		if (item == null) throw new ResourceNotFoundException("BacklogItem", value);
		return item;
	}

	private List<String> values(List<String> values) {
		if (values == null) return List.of();
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String value : values) normalized.add(required(value, "List values cannot be blank"));
		return new ArrayList<>(normalized);
	}

	private Map<String, Object> metadata(BacklogItem item) {
		Map<String, Object> values = new java.util.LinkedHashMap<>();
		values.put("priority", item.getPriority().name());
		values.put("sourceType", item.getSourceType().name());
		values.put("dependencyCount", item.getDependsOn().size());
		if (item.getProjectId() != null) values.put("projectId", item.getProjectId());
		if (item.getWorkspaceId() != null) values.put("workspaceId", item.getWorkspaceId());
		if (item.getConvertedTaskId() != null) values.put("convertedTaskId", item.getConvertedTaskId());
		return Map.copyOf(values);
	}

	private static EventType transitionEvent(BacklogStatus from, BacklogStatus to) {
		if (to == BacklogStatus.BLOCKED) return EventType.BACKLOG_BLOCKED;
		if (from == BacklogStatus.BLOCKED) return EventType.BACKLOG_UNBLOCKED;
		if (to == BacklogStatus.CANCELLED) return EventType.BACKLOG_CANCELLED;
		return EventType.BACKLOG_STATUS_CHANGED;
	}

	private static Map<BacklogStatus, Set<BacklogStatus>> transitions() {
		Map<BacklogStatus, Set<BacklogStatus>> values = new EnumMap<>(BacklogStatus.class);
		values.put(BacklogStatus.IDEA, Set.of(BacklogStatus.PLANNED, BacklogStatus.CANCELLED));
		values.put(BacklogStatus.PLANNED, Set.of(BacklogStatus.READY, BacklogStatus.BLOCKED, BacklogStatus.CANCELLED));
		values.put(BacklogStatus.READY, Set.of(BacklogStatus.PLANNED, BacklogStatus.BLOCKED, BacklogStatus.CANCELLED));
		values.put(BacklogStatus.BLOCKED, Set.of(BacklogStatus.PLANNED, BacklogStatus.READY, BacklogStatus.CANCELLED));
		values.put(BacklogStatus.CONVERTED, Set.of(BacklogStatus.DONE));
		return Map.copyOf(values);
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
		return value.trim();
	}
	private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
	private record Context(String projectId, String workspaceId) { }
}
