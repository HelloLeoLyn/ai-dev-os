package com.aidevos.orchestrator.controller;

import java.util.List;
import com.aidevos.orchestrator.analysis.RecommendationService;
import com.aidevos.orchestrator.analysis.RecommendationStatus;
import com.aidevos.orchestrator.analysis.RecommendationView;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RecommendationControllerTest {
	@Test void exposesReadAndDecisionEndpointsWithoutGetSideEffects()throws Exception{
		RecommendationService service=mock(RecommendationService.class);RecommendationView view=new RecommendationView(
			"r1","t","e","title","why",null,null,null,0,List.of(),List.of(),null,false,List.of(),List.of(),null,
			RecommendationStatus.NEW,null,null,null,null,null);
		when(service.get("r1")).thenReturn(view);
		var mvc=standaloneSetup(new RecommendationController(service)).build();
		mvc.perform(get("/api/recommendations/r1")).andExpect(status().isOk());verify(service).get("r1");verifyNoMoreInteractions(service);
		mvc.perform(post("/api/recommendations/r1/view").contentType("application/json").content("{\"actor\":\"alice\"}"));
		verify(service).view("r1","alice");
		mvc.perform(post("/api/recommendations/r1/defer").contentType("application/json").content("{\"reason\":\"later\"}"));
		verify(service).defer("r1",null,"later",null);
		mvc.perform(post("/api/recommendations/r1/ignore").contentType("application/json").content("{\"reason\":\"no\"}"));
		verify(service).ignore("r1","no",null);
		mvc.perform(post("/api/recommendations/r1/work-item").contentType("application/json").content("{}"));
		verify(service).createWorkItem(eq("r1"),any());
	}
}
