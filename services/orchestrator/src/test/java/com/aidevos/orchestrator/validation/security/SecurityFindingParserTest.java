package com.aidevos.orchestrator.validation.security;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class SecurityFindingParserTest {
	private static final String SECRET = "TEST_SECRET_VALUE_123456";
	private final SecurityRedactor redactor = new SecurityRedactor();
	private final SecurityFindingParser parser = new SecurityFindingParser(new ObjectMapper(), redactor);

	@Test void gitleaksDetectsSecretWithoutRetainingSecretValue() {
		String raw = """
			[{"RuleID":"generic-api-key","Description":"API key","File":"config.yml","StartLine":3,
			  "Secret":"TEST_SECRET_VALUE_123456","Match":"api_key = TEST_SECRET_VALUE_123456"}]
			""";
		SecurityFinding finding = parser.parse(SecurityScannerType.GITLEAKS, raw).getFirst();
		assertEquals(SecurityCategory.SECRET, finding.getCategory());
		assertEquals(SecuritySeverity.HIGH, finding.getSeverity());
		assertTrue(finding.isBlockingCandidate());
		assertFalse(new ObjectMapper().writeValueAsString(finding).contains(SECRET));
		assertFalse(redactor.redact(raw).contains(SECRET));
	}

	@Test void gitleaksNoSecretProducesNoFindings() {
		assertTrue(parser.parse(SecurityScannerType.GITLEAKS, "[]").isEmpty());
	}

	@Test void semgrepMapsSeverityAndRejectsMalformedOutput() {
		String raw = """
			{"results":[{"check_id":"java.lang.security.audit.command-injection",
			"path":"src/Main.java","start":{"line":12},
			"extra":{"severity":"WARNING","message":"Untrusted input reaches command"}}]}
			""";
		SecurityFinding finding = parser.parse(SecurityScannerType.SEMGREP, raw).getFirst();
		assertEquals(SecurityCategory.SAST, finding.getCategory());
		assertEquals(SecuritySeverity.MEDIUM, finding.getSeverity());
		assertThrows(IllegalArgumentException.class,
			() -> parser.parse(SecurityScannerType.SEMGREP, "{broken"));
	}

	@Test void trivyMapsVulnerabilityAndMisconfiguration() {
		String raw = """
			{"Results":[{"Target":"pom.xml","Vulnerabilities":[{
			"VulnerabilityID":"CVE-2025-0001","PkgName":"demo","InstalledVersion":"1.0",
			"FixedVersion":"1.1","Severity":"CRITICAL","Title":"Known issue"}],
			"Misconfigurations":[{"ID":"AVD-TEST-1","Severity":"HIGH","Title":"Unsafe config",
			"Message":"Harden this setting","CauseMetadata":{"StartLine":8}}]}]}
			""";
		List<SecurityFinding> findings = parser.parse(SecurityScannerType.TRIVY, raw);
		assertEquals(2, findings.size());
		assertEquals(SecurityCategory.DEPENDENCY, findings.get(0).getCategory());
		assertEquals("CVE-2025-0001", findings.get(0).getVulnerabilityId());
		assertEquals(SecuritySeverity.CRITICAL, findings.get(0).getSeverity());
		assertEquals(SecurityCategory.CONFIGURATION, findings.get(1).getCategory());
	}

	@Test void fingerprintIsStableAlthoughFindingIdIsNot() {
		String raw = "[{\"RuleID\":\"rule\",\"File\":\"a.txt\",\"StartLine\":1}]";
		SecurityFinding first = parser.parse(SecurityScannerType.GITLEAKS, raw).getFirst();
		SecurityFinding second = parser.parse(SecurityScannerType.GITLEAKS, raw).getFirst();
		assertEquals(first.getFingerprint(), second.getFingerprint());
		assertNotEquals(first.getFindingId(), second.getFindingId());
	}
}
