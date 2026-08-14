package com.aidevos.orchestrator.controller;
import java.util.List;
import com.aidevos.orchestrator.network.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/settings/network")
public class NetworkSettingsController {
	private final NetworkSettingsService settings;private final NetworkProbeService probes;
	public NetworkSettingsController(NetworkSettingsService s,NetworkProbeService p){settings=s;probes=p;}
	@GetMapping public ProxySettingsView get(){return settings.get();}
	@PutMapping public ProxySettingsView save(@RequestBody ProxySettings input){return settings.save(input);}
	@PostMapping("/probes") public List<NetworkProbeResult> probes(){return probes.probe();}
}
