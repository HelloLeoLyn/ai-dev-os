package com.aidevos.orchestrator.analysis;

import java.nio.charset.StandardCharsets;

/** Server-owned identity for a recommendation within an analysis. */
public final class RecommendationIdentity {
    private static final String PREFIX = "recommendation-";
    private RecommendationIdentity() { }

    public static String global(String analysisId, String localRecommendationId) {
        if (analysisId == null || analysisId.isBlank() || localRecommendationId == null
                || localRecommendationId.isBlank()) {
            throw new IllegalArgumentException("analysisId and localRecommendationId are required");
        }
        try {
            var digest = java.security.MessageDigest.getInstance("MD5");
            return PREFIX + java.util.HexFormat.of().formatHex(digest.digest(("analysis:" + analysisId
                    + ":recommendation:" + localRecommendationId).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 is required for deterministic recommendation identity", exception);
        }
    }

    public static boolean isGlobal(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
