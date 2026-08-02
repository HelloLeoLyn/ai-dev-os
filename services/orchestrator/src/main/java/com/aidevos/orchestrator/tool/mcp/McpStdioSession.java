package com.aidevos.orchestrator.tool.mcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class McpStdioSession implements McpSession {

	private final List<String> command;
	private final String workingDirectory;
	private final ObjectMapper objectMapper;
	private final AtomicLong requestIds = new AtomicLong();
	private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
	private final Object writeLock = new Object();
	private volatile Process process;
	private volatile BufferedWriter writer;
	private volatile Thread readerThread;
	private volatile Thread errorThread;

	public McpStdioSession(List<String> command, String workingDirectory, ObjectMapper objectMapper) {
		if (command == null || command.isEmpty()) {
			throw new IllegalArgumentException("MCP server command is required");
		}
		this.command = List.copyOf(command);
		this.workingDirectory = workingDirectory;
		this.objectMapper = objectMapper;
	}

	@Override
	public synchronized void connect() {
		if (isConnected()) {
			return;
		}
		try {
			ProcessBuilder builder = new ProcessBuilder(command);
			if (workingDirectory != null && !workingDirectory.isBlank()) {
				builder.directory(new java.io.File(workingDirectory));
			}
			process = builder.start();
			writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),
				StandardCharsets.UTF_8));
			readerThread = Thread.ofVirtual().name("mcp-stdio-reader").start(this::readResponses);
			errorThread = Thread.ofVirtual().name("mcp-stderr-reader").start(this::drainErrors);
		}
		catch (IOException exception) {
			close();
			throw new McpException("MCP_SERVER_UNAVAILABLE", "Unable to start MCP server", exception);
		}
	}

	@Override
	public JsonNode request(String method, Map<String, Object> parameters, Duration timeout) {
		ensureConnected();
		long id = requestIds.incrementAndGet();
		CompletableFuture<JsonNode> response = new CompletableFuture<>();
		pending.put(id, response);
		try {
			write(new McpRequest("2.0", id, method, parameters == null ? Map.of() : parameters));
			JsonNode message = response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			JsonNode error = message.get("error");
			if (error != null && !error.isNull()) {
				throw new McpException("MCP_PROTOCOL_ERROR", errorMessage(error));
			}
			JsonNode result = message.get("result");
			if (result == null) {
				throw new McpException("MCP_INVALID_RESPONSE", "MCP response has no result");
			}
			return result;
		}
		catch (java.util.concurrent.TimeoutException exception) {
			throw new McpException("MCP_TIMEOUT", "MCP request timed out: " + method, exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new McpException("MCP_INTERRUPTED", "MCP request interrupted: " + method, exception);
		}
		catch (java.util.concurrent.ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof McpException mcpException) {
				throw mcpException;
			}
			throw new McpException("MCP_DISCONNECTED", "MCP session disconnected", cause);
		}
		finally {
			pending.remove(id);
		}
	}

	@Override
	public void notify(String method, Map<String, Object> parameters) {
		ensureConnected();
		write(new McpNotification("2.0", method, parameters == null ? Map.of() : parameters));
	}

	@Override
	public boolean isConnected() {
		Process current = process;
		return current != null && current.isAlive() && writer != null;
	}

	private void ensureConnected() {
		if (!isConnected()) {
			throw new McpException("MCP_DISCONNECTED", "MCP session is not connected");
		}
	}

	private void write(Object message) {
		try {
			synchronized (writeLock) {
				writer.write(objectMapper.writeValueAsString(message));
				writer.newLine();
				writer.flush();
			}
		}
		catch (IOException exception) {
			throw new McpException("MCP_TRANSPORT_ERROR", "Unable to write MCP message", exception);
		}
	}

	private void readResponses() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				JsonNode message = objectMapper.readTree(line);
				JsonNode id = message.get("id");
				if (id != null && id.canConvertToLong()) {
					CompletableFuture<JsonNode> response = pending.get(id.asLong());
					if (response != null) {
						response.complete(message);
					}
				}
			}
			failPending(new McpException("MCP_DISCONNECTED", "MCP server closed stdout"));
		}
		catch (Exception exception) {
			failPending(new McpException("MCP_TRANSPORT_ERROR", "Unable to read MCP response", exception));
		}
	}

	private void drainErrors() {
		try (var input = process.getErrorStream()) {
			input.transferTo(java.io.OutputStream.nullOutputStream());
		}
		catch (IOException ignored) {
			// stderr is intentionally drained without logging server or credential data.
		}
	}

	private void failPending(McpException exception) {
		pending.values().forEach(future -> future.completeExceptionally(exception));
		pending.clear();
	}

	private String errorMessage(JsonNode error) {
		JsonNode code = error.get("code");
		JsonNode message = error.get("message");
		return "MCP error " + (code == null ? "unknown" : code.asText()) + ": "
			+ (message == null ? "Unknown error" : message.asText());
	}

	@Override
	public synchronized void close() {
		BufferedWriter currentWriter = writer;
		writer = null;
		if (currentWriter != null) {
			try {
				currentWriter.close();
			}
			catch (IOException ignored) {
			}
		}
		Process current = process;
		process = null;
		if (current != null) {
			current.descendants().forEach(ProcessHandle::destroyForcibly);
			current.destroyForcibly();
		}
		failPending(new McpException("MCP_DISCONNECTED", "MCP session closed"));
	}

	private record McpRequest(String jsonrpc, long id, String method, Map<String, Object> params) {
	}

	private record McpNotification(String jsonrpc, String method, Map<String, Object> params) {
	}
}
