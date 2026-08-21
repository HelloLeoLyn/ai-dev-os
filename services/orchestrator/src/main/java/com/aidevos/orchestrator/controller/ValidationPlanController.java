package com.aidevos.orchestrator.controller;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationMode;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1 Validation Plan API（只生成计划，不执行）。
 *
 * POST /api/tasks/{taskId}/validation-plan
 * body（可选）：{ "mode": "AUTO", "profile": "TARGETED" }
 * changeSetId 与 changed files 从该 task 的最新 ChangeSet 解析。
 */
@RestController
@RequestMapping("/api/tasks")
public class ValidationPlanController {

	private final ValidationPlanService validationPlanService;
	private final ChangeService changeService;

	public ValidationPlanController(ValidationPlanService validationPlanService,
			ChangeService changeService) {
		this.validationPlanService = validationPlanService;
		this.changeService = changeService;
	}

	@PostMapping("/{taskId}/validation-plan")
	public ResponseEntity<?> generate(@PathVariable String taskId,
			@RequestBody(required = false) PlanRequest request) {
		List<ChangeSet> changes = changeService.getChangesByTask(taskId);
		if (changes.isEmpty()) {
			return ResponseEntity.badRequest().body("No ChangeSet found for task: " + taskId);
		}
		ChangeSet change = changes.get(changes.size() - 1);
		ValidationMode mode = request != null && request.mode() != null
			&& !request.mode().isBlank()
			? ValidationMode.valueOf(request.mode().trim().toUpperCase())
			: ValidationMode.AUTO;
		List<String> files = DiffFiles.parse(change.getDiff());
		ValidationPlan plan = validationPlanService.generate(taskId, change.getChangeId(),
			files, mode, request == null ? null : request.profile());
		return ResponseEntity.ok(plan);
	}

	public record PlanRequest(String mode, String profile) {
	}

	/** git diff 文本 → changed file paths（diff --git a/x b/y 行取 b/ 路径）。 */
	static final class DiffFiles {

		private DiffFiles() {
		}

		static List<String> parse(String diff) {
			List<String> files = new ArrayList<>();
			if (diff == null) {
				return files;
			}
			for (String line : diff.split("\\R")) {
				if (line.startsWith("diff --git ")) {
					int bIndex = line.indexOf(" b/");
					if (bIndex >= 0) {
						String path = line.substring(bIndex + 3).trim();
						if (!path.isBlank() && !path.endsWith("/dev/null")) {
							files.add(path);
						}
					}
				}
			}
			return files;
		}
	}
}
