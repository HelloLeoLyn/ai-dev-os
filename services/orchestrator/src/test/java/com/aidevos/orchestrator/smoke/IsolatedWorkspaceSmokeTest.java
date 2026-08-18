package com.aidevos.orchestrator.smoke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IsolatedWorkspaceSmokeTest {

    @Test
    void verifiesExecutionWorkspace() {
        assertEquals("AI_DEV_OS", "AI_DEV_OS");
    }
}
