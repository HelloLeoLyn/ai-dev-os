package com.aidevos.orchestrator.validation.security;
import java.util.List;
public interface SecurityReportRepository { void save(SecurityReport report); SecurityReport get(String id); List<SecurityReport> findByValidationRunId(String runId); }
