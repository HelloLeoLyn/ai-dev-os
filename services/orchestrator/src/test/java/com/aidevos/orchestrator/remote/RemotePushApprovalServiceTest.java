package com.aidevos.orchestrator.remote;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.aidevos.orchestrator.audit.AuditService;

class RemotePushApprovalServiceTest {
    @Test
    void sameCommitRequestIsIdempotentAndConsumedApprovalDoesNotAuthorizeNewCommit() {
        InMemoryRemotePushApprovalRepository repository = new InMemoryRemotePushApprovalRepository();
        RemotePushApprovalService service = new RemotePushApprovalService(repository, AuditService.noop());
        RemotePushApproval first = service.request("task-1", "execution-1", "ai-dev-os/task-task-1", "commit-1", "hash-1", "origin");
        RemotePushApproval same = service.request("task-1", "execution-1", "ai-dev-os/task-task-1", "commit-1", "hash-1", "origin");
        assertSame(first, same);
        assertEquals(RemotePushApprovalStatus.PENDING, first.getStatus());
        service.approve(first.getApprovalId());
        assertTrue(service.consume(first));
        assertEquals(RemotePushApprovalStatus.CONSUMED, first.getStatus());
        RemotePushApproval nextCommit = service.request("task-1", "execution-1", "ai-dev-os/task-task-1", "commit-2", "hash-2", "origin");
        assertNotEquals(first.getApprovalId(), nextCommit.getApprovalId());
        assertEquals("REMOTE", first.getAuthority());
        assertEquals("PUSH_TASK_BRANCH", first.getOperation());
    }
}
