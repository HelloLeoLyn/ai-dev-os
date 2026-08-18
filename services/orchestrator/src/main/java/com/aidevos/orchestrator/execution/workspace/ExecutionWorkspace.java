package com.aidevos.orchestrator.execution.workspace;

import java.time.Instant;

public class ExecutionWorkspace {
    private String id;
    private String taskId;
    private String projectId;
    private String workspaceId;
    private String sourceWorkspace;
    private String executionWorkspace;
    private String strategy;
    private String executionBranch;
    private String baseRevision;
    private Instant createdAt;
    private volatile Instant updatedAt;
    private volatile ExecutionWorkspaceStatus status;
    private String promotionErrorCode;
    private String promotionReason;
    private Instant promotedAt;
    private Instant rejectedAt;

    public ExecutionWorkspace() { }

    public ExecutionWorkspace(String id, String taskId, String projectId, String workspaceId,
            String sourceWorkspace, String executionWorkspace, String strategy,
            ExecutionWorkspaceStatus status, String baseRevision, Instant createdAt, Instant updatedAt) {
        this(id, taskId, projectId, workspaceId, sourceWorkspace, executionWorkspace, strategy,
            null, status, baseRevision, createdAt, updatedAt);
    }
    public ExecutionWorkspace(String id, String taskId, String projectId, String workspaceId,
            String sourceWorkspace, String executionWorkspace, String strategy, String executionBranch,
            ExecutionWorkspaceStatus status, String baseRevision, Instant createdAt, Instant updatedAt) {
        this.id=id; this.taskId=taskId; this.projectId=projectId; this.workspaceId=workspaceId;
        this.sourceWorkspace=sourceWorkspace; this.executionWorkspace=executionWorkspace;
        this.executionBranch=executionBranch;
        this.strategy=strategy; this.status=status == null ? ExecutionWorkspaceStatus.CREATING : status;
        this.baseRevision=baseRevision; this.createdAt=createdAt == null ? Instant.now() : createdAt;
        this.updatedAt=updatedAt == null ? this.createdAt : updatedAt;
    }
    public synchronized void mark(ExecutionWorkspaceStatus value) { status=value; updatedAt=Instant.now(); }
    public String getId(){return id;} public String getTaskId(){return taskId;} public String getProjectId(){return projectId;}
    public String getWorkspaceId(){return workspaceId;} public String getSourceWorkspace(){return sourceWorkspace;}
    public String getExecutionWorkspace(){return executionWorkspace;} public String getStrategy(){return strategy;}
    public String getExecutionBranch(){return executionBranch;}
    public String getBaseRevision(){return baseRevision;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public ExecutionWorkspaceStatus getStatus(){return status;}
    public void setId(String value){id=value;} public void setTaskId(String value){taskId=value;}
    public void setProjectId(String value){projectId=value;} public void setWorkspaceId(String value){workspaceId=value;}
    public void setSourceWorkspace(String value){sourceWorkspace=value;} public void setExecutionWorkspace(String value){executionWorkspace=value;}
    public void setStrategy(String value){strategy=value;} public void setBaseRevision(String value){baseRevision=value;}
    public void setExecutionBranch(String value){executionBranch=value;}
    public void setCreatedAt(Instant value){createdAt=value;} public void setUpdatedAt(Instant value){updatedAt=value;}
    public void setStatus(ExecutionWorkspaceStatus value){status=value;}
    public String getPromotionErrorCode(){return promotionErrorCode;}
    public void setPromotionErrorCode(String value){promotionErrorCode=value;}
    public String getPromotionReason(){return promotionReason;}
    public void setPromotionReason(String value){promotionReason=value;}
    public Instant getPromotedAt(){return promotedAt;}
    public void setPromotedAt(Instant value){promotedAt=value;}
    public Instant getRejectedAt(){return rejectedAt;}
    public void setRejectedAt(Instant value){rejectedAt=value;}
}
