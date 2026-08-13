package com.aidevos.orchestrator.validation.security;

import java.time.Instant; import java.util.ArrayList; import java.util.EnumMap; import java.util.List; import java.util.Map;
public class SecurityReport {
	private String reportId,taskId,projectId,workspaceId,validationRunId,summary;
	private SecurityScannerType scanner; private SecurityScanStatus status; private List<SecurityFinding> findings=new ArrayList<>();
	private Map<SecuritySeverity,Integer> countsBySeverity=new EnumMap<>(SecuritySeverity.class);
	private Instant startedAt,completedAt; private long durationMs; private List<String> artifactIds=new ArrayList<>();
	public SecurityReport(){} public String getReportId(){return reportId;} public void setReportId(String v){reportId=v;}
	public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;} public String getProjectId(){return projectId;} public void setProjectId(String v){projectId=v;}
	public String getWorkspaceId(){return workspaceId;} public void setWorkspaceId(String v){workspaceId=v;} public String getValidationRunId(){return validationRunId;} public void setValidationRunId(String v){validationRunId=v;}
	public SecurityScannerType getScanner(){return scanner;} public void setScanner(SecurityScannerType v){scanner=v;} public SecurityScanStatus getStatus(){return status;} public void setStatus(SecurityScanStatus v){status=v;}
	public List<SecurityFinding> getFindings(){return findings;} public void setFindings(List<SecurityFinding> v){findings=v==null?new ArrayList<>():new ArrayList<>(v);}
	public Map<SecuritySeverity,Integer> getCountsBySeverity(){return countsBySeverity;} public void setCountsBySeverity(Map<SecuritySeverity,Integer> v){countsBySeverity=new EnumMap<>(SecuritySeverity.class);if(v!=null)countsBySeverity.putAll(v);}
	public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;} public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
	public long getDurationMs(){return durationMs;} public void setDurationMs(long v){durationMs=v;} public List<String> getArtifactIds(){return artifactIds;} public void setArtifactIds(List<String> v){artifactIds=v==null?new ArrayList<>():new ArrayList<>(v);}
	public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
}
