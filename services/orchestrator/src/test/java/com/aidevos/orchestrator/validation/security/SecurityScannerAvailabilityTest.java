package com.aidevos.orchestrator.validation.security;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityScannerAvailabilityTest {
	@Test void distinguishesAvailableUnavailableAndError() {
		CommandExecutor executor = mock(CommandExecutor.class);
		CommandResult result = new CommandResult(); result.setSuccess(true); result.setOutput("gitleaks 8.24.0\n");
		when(executor.execute(any(CommandOptions.class))).thenReturn(result);
		assertEquals(ScannerAvailabilityStatus.AVAILABLE,
			new SecurityScannerAvailability(executor).detect(SecurityScannerType.GITLEAKS).status());

		result.setSuccess(false); result.setExitCode(-1); result.setError("Cannot run program: No such file or directory");
		assertEquals(ScannerAvailabilityStatus.UNAVAILABLE,
			new SecurityScannerAvailability(executor).detect(SecurityScannerType.SEMGREP).status());

		result.setExitCode(2); result.setError("scanner crashed");
		assertEquals(ScannerAvailabilityStatus.ERROR,
			new SecurityScannerAvailability(executor).detect(SecurityScannerType.TRIVY).status());
		verify(executor, times(3)).execute(any(CommandOptions.class));
	}
}
