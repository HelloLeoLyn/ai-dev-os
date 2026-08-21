package com.aidevos.orchestrator.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only system API exposing product identity and version information.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

	@GetMapping("/version")
	public Map<String, String> version() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("name", "AI Dev OS");
		body.put("version", "v1");
		return body;
	}
}
