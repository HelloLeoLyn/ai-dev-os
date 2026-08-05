package com.aidevos.orchestrator.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness and readiness probes. Liveness is process-level and always UP once
 * the application is running; readiness is 200 only after startup completes
 * and PostgreSQL migrations are applied, and 503 otherwise.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

	private final ReadinessGate readinessGate;

	public HealthController(ReadinessGate readinessGate) {
		this.readinessGate = readinessGate;
	}

	@GetMapping
	public Map<String, Object> health() {
		return Map.of("status", "UP");
	}

	@GetMapping("/readiness")
	public ResponseEntity<Map<String, Object>> readiness() {
		boolean ready = readinessGate.isReady();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", ready ? "READY" : "NOT_READY");
		body.put("details", readinessGate.details());
		return ready ? ResponseEntity.ok(body)
			: ResponseEntity.status(503).body(body);
	}
}
