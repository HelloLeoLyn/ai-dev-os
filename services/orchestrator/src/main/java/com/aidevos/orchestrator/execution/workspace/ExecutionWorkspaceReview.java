package com.aidevos.orchestrator.execution.workspace;

import java.util.ArrayList;
import java.util.List;

public class ExecutionWorkspaceReview {
    private String taskId;
    private String status;
    private String sourceWorkspace;
    private String executionWorkspace;
    private String baseRevision;
    private String sourceRevision;
    private String diff;
    private String diffCheck;
    private String changeStat;
    private List<String> changedFiles = new ArrayList<>();
    private List<String> untrackedFiles = new ArrayList<>();
    private List<String> artifacts = new ArrayList<>();
    private String errorCode;
    private String reason;

    public String getTaskId(){return taskId;} public void setTaskId(String value){taskId=value;}
    public String getStatus(){return status;} public void setStatus(String value){status=value;}
    public String getSourceWorkspace(){return sourceWorkspace;} public void setSourceWorkspace(String value){sourceWorkspace=value;}
    public String getExecutionWorkspace(){return executionWorkspace;} public void setExecutionWorkspace(String value){executionWorkspace=value;}
    public String getBaseRevision(){return baseRevision;} public void setBaseRevision(String value){baseRevision=value;}
    public String getSourceRevision(){return sourceRevision;} public void setSourceRevision(String value){sourceRevision=value;}
    public String getDiff(){return diff;} public void setDiff(String value){diff=value;}
    public String getDiffCheck(){return diffCheck;} public void setDiffCheck(String value){diffCheck=value;}
    public String getChangeStat(){return changeStat;} public void setChangeStat(String value){changeStat=value;}
    public List<String> getChangedFiles(){return changedFiles;} public void setChangedFiles(List<String> value){changedFiles=value == null ? new ArrayList<>() : new ArrayList<>(value);}
    public List<String> getUntrackedFiles(){return untrackedFiles;} public void setUntrackedFiles(List<String> value){untrackedFiles=value == null ? new ArrayList<>() : new ArrayList<>(value);}
    public List<String> getArtifacts(){return artifacts;} public void setArtifacts(List<String> value){artifacts=value == null ? new ArrayList<>() : new ArrayList<>(value);}
    public String getErrorCode(){return errorCode;} public void setErrorCode(String value){errorCode=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
}
