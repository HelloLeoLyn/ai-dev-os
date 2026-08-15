package com.aidevos.orchestrator.controller;

import java.time.Instant;
import com.aidevos.orchestrator.analysis.CreateRecommendationWorkItemRequest;
import com.aidevos.orchestrator.analysis.RecommendationService;
import com.aidevos.orchestrator.analysis.RecommendationView;
import com.aidevos.orchestrator.analysis.RecommendationWorkItemResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations/{recommendationId}")
public class RecommendationController {
	private final RecommendationService service;
	public RecommendationController(RecommendationService service){this.service=service;}
	@GetMapping public RecommendationView get(@PathVariable String recommendationId){return service.get(recommendationId);}
	@PostMapping("/view") public RecommendationView view(@PathVariable String recommendationId,
			@RequestBody(required=false) ActorRequest request){return service.view(recommendationId,request==null?null:request.actor());}
	@PostMapping("/defer") public RecommendationView defer(@PathVariable String recommendationId,
			@RequestBody(required=false) DeferRequest request){return service.defer(recommendationId,
			request==null?null:request.deferUntil(),request==null?null:request.reason(),request==null?null:request.actor());}
	@PostMapping("/ignore") public RecommendationView ignore(@PathVariable String recommendationId,
			@RequestBody(required=false) IgnoreRequest request){return service.ignore(recommendationId,
			request==null?null:request.reason(),request==null?null:request.actor());}
	@PostMapping("/work-item") public ResponseEntity<RecommendationWorkItemResult> workItem(
			@PathVariable String recommendationId,@RequestBody(required=false) CreateRecommendationWorkItemRequest request){
		return ResponseEntity.ok(service.createWorkItem(recommendationId,request));}
	public record ActorRequest(String actor){}
	public record DeferRequest(Instant deferUntil,String reason,String actor){}
	public record IgnoreRequest(String reason,String actor){}
}
