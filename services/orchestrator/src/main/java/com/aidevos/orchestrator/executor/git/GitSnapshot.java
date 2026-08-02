package com.aidevos.orchestrator.executor.git;

import java.util.List;

public record GitSnapshot(String branch, String head, String status, String diffStat,
		String patch, String cachedDiff, List<String> untrackedFiles) {
}
