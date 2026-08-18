package com.aidevos.orchestrator.remote;

import java.time.Instant;

public final class RemotePushApproval {
    public static final String AUTHORITY = "REMOTE";
    public static final String OPERATION = "PUSH_TASK_BRANCH";
    private final String approvalId;
    private final String taskId;
    private final String executionWorkspaceId;
    private final String executionBranch;
    private final String commitId;
    private final String commitHash;
    private final String remote;
    private final String targetRef;
    private final Instant createdAt;
    private Instant updatedAt;
    private RemotePushApprovalStatus status;

    public RemotePushApproval(String approvalId, String taskId, String executionWorkspaceId,
            String executionBranch, String commitId, String commitHash, String remote,
            String targetRef, Instant createdAt) {
        this(approvalId, taskId, executionWorkspaceId, executionBranch, commitId, commitHash,
            remote, targetRef, createdAt, createdAt, RemotePushApprovalStatus.PENDING);
    }
    private RemotePushApproval(String approvalId, String taskId, String executionWorkspaceId,
            String executionBranch, String commitId, String commitHash, String remote,
            String targetRef, Instant createdAt, Instant updatedAt, RemotePushApprovalStatus status) {
        this.approvalId=approvalId; this.taskId=taskId; this.executionWorkspaceId=executionWorkspaceId;
        this.executionBranch=executionBranch; this.commitId=commitId; this.commitHash=commitHash;
        this.remote=remote; this.targetRef=targetRef; this.createdAt=createdAt; this.updatedAt=updatedAt;
        this.status=status == null ? RemotePushApprovalStatus.PENDING : status;
    }
    public static RemotePushApproval restore(String approvalId, String taskId, String workspaceId,
            String branch, String commitId, String hash, String remote, String targetRef,
            Instant createdAt, Instant updatedAt, RemotePushApprovalStatus status) {
        return new RemotePushApproval(approvalId, taskId, workspaceId, branch, commitId, hash,
            remote, targetRef, createdAt, updatedAt, status);
    }
    public synchronized void approve() { if (status == RemotePushApprovalStatus.PENDING) { status=RemotePushApprovalStatus.APPROVED; updatedAt=Instant.now(); } }
    public synchronized void reject() { if (status == RemotePushApprovalStatus.PENDING) { status=RemotePushApprovalStatus.REJECTED; updatedAt=Instant.now(); } }
    public synchronized boolean consume() { if (status != RemotePushApprovalStatus.APPROVED) return false; status=RemotePushApprovalStatus.CONSUMED; updatedAt=Instant.now(); return true; }
    public String getApprovalId(){return approvalId;} public String getTaskId(){return taskId;}
    public String getExecutionWorkspaceId(){return executionWorkspaceId;} public String getExecutionBranch(){return executionBranch;}
    public String getCommitId(){return commitId;} public String getCommitHash(){return commitHash;}
    public String getRemote(){return remote;} public String getTargetRef(){return targetRef;}
    public String getAuthority(){return AUTHORITY;} public String getOperation(){return OPERATION;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public synchronized RemotePushApprovalStatus getStatus(){return status;}
}
