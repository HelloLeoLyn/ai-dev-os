package com.aidevos.orchestrator.validation.provider; import com.aidevos.orchestrator.validation.security.*; import org.springframework.stereotype.Component;
@Component public class TrivyValidationProvider extends AbstractSecurityValidationProvider { public TrivyValidationProvider(SecurityValidationService s){super(s,SecurityScannerType.TRIVY);} }
