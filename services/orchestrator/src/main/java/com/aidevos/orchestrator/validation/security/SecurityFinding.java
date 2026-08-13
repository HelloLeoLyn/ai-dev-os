package com.aidevos.orchestrator.validation.security;

import java.util.LinkedHashMap;
import java.util.Map;

public class SecurityFinding {
	private String findingId; private SecurityScannerType scanner; private SecurityCategory category;
	private SecuritySeverity severity; private String ruleId; private String title; private String message;
	private String file; private Integer line; private Integer column; private String packageName;
	private String installedVersion; private String fixedVersion; private String vulnerabilityId;
	private String recommendation; private boolean blockingCandidate; private String fingerprint;
	private Map<String,Object> metadata=new LinkedHashMap<>();
	public SecurityFinding() { }
	public String getFindingId(){return findingId;} public void setFindingId(String v){findingId=v;}
	public SecurityScannerType getScanner(){return scanner;} public void setScanner(SecurityScannerType v){scanner=v;}
	public SecurityCategory getCategory(){return category;} public void setCategory(SecurityCategory v){category=v;}
	public SecuritySeverity getSeverity(){return severity;} public void setSeverity(SecuritySeverity v){severity=v;}
	public String getRuleId(){return ruleId;} public void setRuleId(String v){ruleId=v;}
	public String getTitle(){return title;} public void setTitle(String v){title=v;}
	public String getMessage(){return message;} public void setMessage(String v){message=v;}
	public String getFile(){return file;} public void setFile(String v){file=v;}
	public Integer getLine(){return line;} public void setLine(Integer v){line=v;}
	public Integer getColumn(){return column;} public void setColumn(Integer v){column=v;}
	public String getPackageName(){return packageName;} public void setPackageName(String v){packageName=v;}
	public String getInstalledVersion(){return installedVersion;} public void setInstalledVersion(String v){installedVersion=v;}
	public String getFixedVersion(){return fixedVersion;} public void setFixedVersion(String v){fixedVersion=v;}
	public String getVulnerabilityId(){return vulnerabilityId;} public void setVulnerabilityId(String v){vulnerabilityId=v;}
	public String getRecommendation(){return recommendation;} public void setRecommendation(String v){recommendation=v;}
	public boolean isBlockingCandidate(){return blockingCandidate;} public void setBlockingCandidate(boolean v){blockingCandidate=v;}
	public String getFingerprint(){return fingerprint;} public void setFingerprint(String v){fingerprint=v;}
	public Map<String,Object> getMetadata(){return metadata;} public void setMetadata(Map<String,Object> v){metadata=v==null?new LinkedHashMap<>():new LinkedHashMap<>(v);}
}
