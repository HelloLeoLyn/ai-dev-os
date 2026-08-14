package com.aidevos.orchestrator.network;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NetworkProbeService {
	private static final Duration TIMEOUT=Duration.ofSeconds(8);
	private final NetworkAwareHttpClientFactory clients;
	public NetworkProbeService(NetworkAwareHttpClientFactory clients){this.clients=clients;}
	public List<NetworkProbeResult> probe(){return List.of(probe("OpenClaw Gateway","http://127.0.0.1:18789/"),probe("CDP","http://127.0.0.1:9333/json/version"),probe("GitHub","https://github.com/"),probe("npm registry","https://registry.npmjs.org/"),probe("Trivy DB","https://github.com/aquasecurity/trivy-db"));}
	NetworkProbeResult probe(String target,String value){long start=System.nanoTime();URI uri=URI.create(value);NetworkRoute route;try{route=clients.route(uri);HttpRequest request=HttpRequest.newBuilder(uri).timeout(TIMEOUT).method("HEAD",HttpRequest.BodyPublishers.noBody()).build();HttpResponse<Void> response=clients.client(TIMEOUT).send(request,HttpResponse.BodyHandlers.discarding());boolean ok=response.statusCode()>0&&response.statusCode()<500;return new NetworkProbeResult(target,value,route,ok,elapsed(start),ok?null:"HTTP_"+response.statusCode());}catch(ProxyResolutionException e){return new NetworkProbeResult(target,value,NetworkRoute.FAILED,false,elapsed(start),ProxyResolutionException.CODE);}catch(Exception e){return new NetworkProbeResult(target,value,NetworkRoute.FAILED,false,elapsed(start),"NETWORK_PROBE_FAILED");}}
	private long elapsed(long start){return (System.nanoTime()-start)/1_000_000;}
}
