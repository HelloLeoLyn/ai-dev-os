package com.aidevos.orchestrator.network;
public record NetworkProbeResult(String target,String url,NetworkRoute route,boolean success,long durationMs,String errorCode) {}
