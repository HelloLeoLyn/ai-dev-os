package com.aidevos.orchestrator.network;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NetworkProbeServiceTest {
	@Test void resolutionFailureIsFailedNotZeroFindingsOrDirect(){NetworkAwareHttpClientFactory clients=mock(NetworkAwareHttpClientFactory.class);URI uri=URI.create("https://github.com/");when(clients.route(uri)).thenReturn(NetworkRoute.PROXY);when(clients.client(any(Duration.class))).thenThrow(new ProxyResolutionException());NetworkProbeResult result=new NetworkProbeService(clients).probe("GitHub",uri.toString());assertFalse(result.success());assertEquals(NetworkRoute.FAILED,result.route());assertEquals(ProxyResolutionException.CODE,result.errorCode());}
	@Test void unreachableLocalTargetIsFailedRatherThanProxy(){NetworkAwareHttpClientFactory clients=mock(NetworkAwareHttpClientFactory.class);URI uri=URI.create("http://127.0.0.1:1/");when(clients.route(uri)).thenReturn(NetworkRoute.DIRECT);when(clients.client(any(Duration.class))).thenReturn(HttpClient.newHttpClient());NetworkProbeResult result=new NetworkProbeService(clients).probe("Gateway",uri.toString());assertFalse(result.success());assertEquals(NetworkRoute.FAILED,result.route());}
}
