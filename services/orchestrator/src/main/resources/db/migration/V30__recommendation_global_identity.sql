ALTER TABLE recommendation_decisions
    ADD COLUMN IF NOT EXISTS identity_version VARCHAR(16) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE recommendation_decisions
    ADD COLUMN IF NOT EXISTS local_recommendation_id VARCHAR(128);

UPDATE recommendation_decisions
SET local_recommendation_id = recommendation_id,
    recommendation_id = 'recommendation-' || md5('analysis:' || analysis_id || ':recommendation:' || recommendation_id),
    identity_version = 'GLOBAL'
WHERE identity_version = 'LEGACY';

UPDATE analysis_insight_sets ais
SET payload = jsonb_set(ais.payload, '{recommendations}', updated.recommendations)
FROM (
    SELECT analysis_id,
           jsonb_agg(
               (recommendation - 'recommendationId') ||
               jsonb_build_object(
                   'recommendationId', 'recommendation-' || md5('analysis:' || analysis_id || ':recommendation:' || local_id),
                   'localRecommendationId', local_id)
               ORDER BY ordinal
           ) AS recommendations
    FROM (
        SELECT ais2.analysis_id, ordinality AS ordinal, recommendation,
               COALESCE(recommendation->>'localRecommendationId', recommendation->>'recommendationId') AS local_id
        FROM analysis_insight_sets ais2
        CROSS JOIN LATERAL jsonb_array_elements(ais2.payload->'recommendations') WITH ORDINALITY AS items(recommendation, ordinality)
    ) source
    GROUP BY analysis_id
) updated
WHERE ais.analysis_id = updated.analysis_id;

-- Rewrite only recommendation Backlog documents whose analysis and source task
-- identify exactly one local recommendation. Documents without that context
-- remain untouched for manual review rather than being guessed.
WITH recommendation_map AS (
    SELECT ais.analysis_id,
           ais.source_task_id,
           COALESCE(rec->>'localRecommendationId', rec->>'recommendationId') AS local_id,
           'recommendation-' || md5('analysis:' || ais.analysis_id || ':recommendation:'
               || COALESCE(rec->>'localRecommendationId', rec->>'recommendationId')) AS global_id,
           COUNT(*) OVER (PARTITION BY ais.analysis_id,
                                      COALESCE(rec->>'localRecommendationId', rec->>'recommendationId')) AS matches
    FROM analysis_insight_sets ais
    CROSS JOIN LATERAL jsonb_array_elements(ais.payload->'recommendations') AS entries(rec)
), unique_map AS (
    SELECT analysis_id, source_task_id, local_id, global_id
    FROM recommendation_map
    WHERE matches = 1
)
UPDATE repository_documents document
SET payload = jsonb_set(
                 jsonb_set(document.payload, '{sourceReference}',
                   to_jsonb(('recommendation:' || mapping.global_id)::text), true),
                 '{recommendationContext,recommendationId}',
                   to_jsonb(mapping.global_id::text), true),
    updated_at = CURRENT_TIMESTAMP
FROM unique_map mapping
WHERE document.repository_type = 'backlog-item'
  AND document.payload->>'sourceReference' = 'recommendation:' || mapping.local_id
  AND document.payload->'recommendationContext'->>'analysisId' = mapping.analysis_id
  AND document.payload->'recommendationContext'->>'sourceTaskId' = mapping.source_task_id;

CREATE INDEX IF NOT EXISTS idx_recommendation_decision_local_id
    ON recommendation_decisions(local_recommendation_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_decision_identity_version
    ON recommendation_decisions(identity_version);
