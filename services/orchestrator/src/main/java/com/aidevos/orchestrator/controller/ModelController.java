package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.modelrouter.ModelProvider;
import com.aidevos.orchestrator.modelrouter.ModelRoute;
import com.aidevos.orchestrator.modelrouter.ModelRouterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

	private final ModelRouterService modelRouterService;

	public ModelController(ModelRouterService modelRouterService) {
		this.modelRouterService = modelRouterService;
	}

	@GetMapping
	public List<ModelProvider> getModels() {
		return modelRouterService.listProviders();
	}

	@GetMapping("/routes")
	public List<ModelRoute> getRoutes() {
		return modelRouterService.listRoutes();
	}
}
