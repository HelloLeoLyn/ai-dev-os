package com.aidevos.orchestrator.validation.provider; import com.aidevos.orchestrator.validation.security.*; import org.springframework.stereotype.Component;
@Component public class SemgrepValidationProvider extends AbstractSecurityValidationProvider { public SemgrepValidationProvider(SecurityValidationService s){super(s,SecurityScannerType.SEMGREP);} }
