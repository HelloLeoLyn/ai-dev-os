package com.aidevos.orchestrator.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationIdentityTest {
    @Test void sameAnalysisAndLocalIdIsStable() {
        assertEquals(RecommendationIdentity.global("analysis-a", "R-001"),
            RecommendationIdentity.global("analysis-a", "R-001"));
    }

    @Test void sameLocalIdInDifferentAnalysesIsIsolated() {
        assertNotEquals(RecommendationIdentity.global("analysis-a", "R-001"),
            RecommendationIdentity.global("analysis-b", "R-001"));
    }
}
