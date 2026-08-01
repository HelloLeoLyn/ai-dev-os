package com.aidevos.orchestrator.openclaw.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class OpenClawDeviceIdentityStore {

	private static final byte[] ED25519_SPKI_PREFIX = HexFormat.of()
		.parseHex("302a300506032b6570032100");

	private static final Set<PosixFilePermission> OWNER_READ_WRITE = Set.of(
			PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

	private final Path path;

	private final ObjectMapper objectMapper;

	private volatile DeviceIdentity cachedIdentity;

	OpenClawDeviceIdentityStore(Path path, ObjectMapper objectMapper) {
		this.path = path;
		this.objectMapper = objectMapper;
	}

	synchronized DeviceIdentity loadOrCreate() {
		if (cachedIdentity != null) {
			return cachedIdentity;
		}
		if (Files.isRegularFile(path)) {
			cachedIdentity = readIdentity();
			return cachedIdentity;
		}
		cachedIdentity = generateIdentity();
		writeIdentity(cachedIdentity);
		return cachedIdentity;
	}

	private DeviceIdentity readIdentity() {
		try {
			JsonNode root = objectMapper.readTree(Files.readString(path));
			if (root.path("version").asInt() != 1) {
				throw new IllegalStateException("Unsupported OpenClaw device identity version");
			}
			String storedDeviceId = requiredText(root, "deviceId");
			byte[] publicKeyDer = decode(requiredText(root, "publicKey"));
			byte[] privateKeyDer = decode(requiredText(root, "privateKey"));
			KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
			PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyDer));
			PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyDer));
			DeviceIdentity identity = createIdentity(publicKey, privateKey);
			if (!identity.deviceId().equals(storedDeviceId)) {
				throw new IllegalStateException("OpenClaw device id does not match its public key");
			}
			verifyKeyPair(identity);
			return identity;
		}
		catch (IOException | GeneralSecurityException | IllegalArgumentException | JacksonException error) {
			throw new IllegalStateException("Unable to load OpenClaw device identity from " + path, error);
		}
	}

	private DeviceIdentity generateIdentity() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
			KeyPair keyPair = generator.generateKeyPair();
			return createIdentity(keyPair.getPublic(), keyPair.getPrivate());
		}
		catch (GeneralSecurityException error) {
			throw new IllegalStateException("Unable to generate OpenClaw device identity", error);
		}
	}

	private DeviceIdentity createIdentity(PublicKey publicKey, PrivateKey privateKey)
			throws GeneralSecurityException {
		byte[] rawPublicKey = rawPublicKey(publicKey);
		String deviceId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
			.digest(rawPublicKey));
		String publicKeyBase64Url = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(rawPublicKey);
		return new DeviceIdentity(deviceId, publicKeyBase64Url, publicKey, privateKey);
	}

	private void verifyKeyPair(DeviceIdentity identity) throws GeneralSecurityException {
		byte[] payload = "openclaw-device-identity-self-check".getBytes(StandardCharsets.UTF_8);
		Signature signer = Signature.getInstance("Ed25519");
		signer.initSign(identity.privateKey());
		signer.update(payload);
		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(identity.publicKey());
		verifier.update(payload);
		if (!verifier.verify(signer.sign())) {
			throw new IllegalStateException("OpenClaw device public and private keys do not match");
		}
	}

	private void writeIdentity(DeviceIdentity identity) {
		Path parent = path.toAbsolutePath().getParent();
		Path temporary = null;
		try {
			Files.createDirectories(parent);
			temporary = Files.createTempFile(parent, ".device-identity-", ".tmp");
			setOwnerOnlyPermissions(temporary);
			String json = objectMapper.writeValueAsString(Map.of(
					"version", 1,
					"deviceId", identity.deviceId(),
					"publicKey", encode(identity.publicKey().getEncoded()),
					"privateKey", encode(identity.privateKey().getEncoded()),
					"createdAtMs", Instant.now().toEpochMilli()));
			Files.writeString(temporary, json + System.lineSeparator(), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, path);
			}
			setOwnerOnlyPermissions(path);
		}
		catch (IOException | JacksonException error) {
			throw new IllegalStateException("Unable to persist OpenClaw device identity to " + path, error);
		}
		finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				}
				catch (IOException ignored) {
				}
			}
		}
	}

	private static byte[] rawPublicKey(PublicKey publicKey) {
		byte[] encoded = publicKey.getEncoded();
		if (encoded.length != ED25519_SPKI_PREFIX.length + 32
				|| !Arrays.equals(ED25519_SPKI_PREFIX,
						Arrays.copyOf(encoded, ED25519_SPKI_PREFIX.length))) {
			throw new IllegalStateException("Unexpected Ed25519 public key encoding");
		}
		return Arrays.copyOfRange(encoded, ED25519_SPKI_PREFIX.length, encoded.length);
	}

	private static String requiredText(JsonNode root, String field) {
		String value = root.path(field).asText();
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("OpenClaw device identity is missing " + field);
		}
		return value;
	}

	private static String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private static byte[] decode(String value) {
		return Base64.getUrlDecoder().decode(value);
	}

	private static void setOwnerOnlyPermissions(Path target) {
		try {
			Files.setPosixFilePermissions(target, OWNER_READ_WRITE);
		}
		catch (UnsupportedOperationException | IOException ignored) {
			// Non-POSIX file systems rely on their native access controls.
		}
	}

	record DeviceIdentity(String deviceId, String publicKeyBase64Url,
			PublicKey publicKey, PrivateKey privateKey) {

		String sign(String payload) {
			try {
				Signature signature = Signature.getInstance("Ed25519");
				signature.initSign(privateKey);
				signature.update(payload.getBytes(StandardCharsets.UTF_8));
				return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
			}
			catch (GeneralSecurityException error) {
				throw new IllegalStateException("Unable to sign OpenClaw device challenge", error);
			}
		}
	}
}
