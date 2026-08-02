const readline = require("node:readline");

const input = readline.createInterface({ input: process.stdin });

function send(message) {
  process.stdout.write(JSON.stringify(message) + "\n");
}

function result(id, value) {
  send({ jsonrpc: "2.0", id, result: value });
}

input.on("line", (line) => {
  const message = JSON.parse(line);
  if (message.method === "notifications/initialized") {
    return;
  }
  if (message.method === "initialize") {
    result(message.id, {
      protocolVersion: "2025-06-18",
      capabilities: { tools: { listChanged: false } },
      serverInfo: { name: "fake-mcp-server", version: "1.0" }
    });
    return;
  }
  if (message.method === "tools/list") {
    result(message.id, {
      tools: [
        {
          name: "echo",
          description: "Echo a value",
          inputSchema: {
            type: "object",
            properties: { value: { type: "string" } },
            required: ["value"]
          },
          annotations: { readOnlyHint: true }
        },
        {
          name: "write_forbidden",
          description: "A write tool used to verify policy",
          inputSchema: { type: "object" },
          annotations: { readOnlyHint: false }
        },
        {
          name: "slow",
          description: "A slow read tool",
          inputSchema: { type: "object" },
          annotations: { readOnlyHint: true }
        },
        {
          name: "protocol_error",
          description: "Return a JSON-RPC error",
          inputSchema: { type: "object" },
          annotations: { readOnlyHint: true }
        }
      ]
    });
    return;
  }
  if (message.method === "tools/call") {
    const name = message.params.name;
    if (name === "protocol_error") {
      send({ jsonrpc: "2.0", id: message.id, error: { code: -32603, message: "fixture failure" } });
      return;
    }
    if (name === "slow") {
      setTimeout(() => result(message.id, {
        content: [{ type: "text", text: "late" }], isError: false
      }), 1000);
      return;
    }
    if (!message.params.arguments || typeof message.params.arguments.value !== "string") {
      result(message.id, {
        content: [{ type: "text", text: "value must be a string" }], isError: true
      });
      return;
    }
    const value = message.params.arguments.value;
    result(message.id, {
      content: [{ type: "text", text: value }],
      structuredContent: { value },
      isError: false
    });
  }
});
