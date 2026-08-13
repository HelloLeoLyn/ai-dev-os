package com.aidevos.orchestrator.validation.provider; import com.aidevos.orchestrator.validation.security.*; import org.springframework.stereotype.Component;
@Component public class GitleaksValidationProvider extends AbstractSecurityValidationProvider { public GitleaksValidationProvider(SecurityValidationService s){super(s,SecurityScannerType.GITLEAKS);} }
