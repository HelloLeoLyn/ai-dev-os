package com.aidevos.orchestrator.openclaw.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClawDeviceIdentityStoreTest {

	@TempDir
	private Path tempDir;

	@Test
	void shouldPersistAndReloadStableEd25519Identity() throws Exception {
		Path identityPath = tempDir.resolve("identity/device.json");
		ObjectMapper objectMapper = new ObjectMapper();
		OpenClawDeviceIdentityStore.DeviceIdentity generated =
				new OpenClawDeviceIdentityStore(identityPath, objectMapper).loadOrCreate();
		OpenClawDeviceIdentityStore.DeviceIdentity reloaded =
				new OpenClawDeviceIdentityStore(identityPath, objectMapper).loadOrCreate();

		assertTrue(Files.isRegularFile(identityPath));
		assertEquals(generated.deviceId(), reloaded.deviceId());
		assertEquals(generated.publicKeyBase64Url(), reloaded.publicKeyBase64Url());
		assertEquals(generated.privateKey(), reloaded.privateKey());

		byte[] rawPublicKey = Base64.getUrlDecoder().decode(generated.publicKeyBase64Url());
		assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
			.digest(rawPublicKey)), generated.deviceId());
	}
}
