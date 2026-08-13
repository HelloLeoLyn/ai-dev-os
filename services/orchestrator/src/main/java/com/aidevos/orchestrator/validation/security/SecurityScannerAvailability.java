package com.aidevos.orchestrator.validation.security;
import java.time.Duration; import java.util.List; import com.aidevos.orchestrator.executor.command.*; import org.springframework.stereotype.Component;
@Component public class SecurityScannerAvailability {
	private final CommandExecutor executor; public SecurityScannerAvailability(CommandExecutor e){executor=e;}
	public ScannerAvailability detect(SecurityScannerType scanner){String binary=scanner.name().toLowerCase(); CommandOptions o=new CommandOptions();o.setCommand(List.of(binary,"--version"));o.setTimeout(Duration.ofSeconds(10));CommandResult r=executor.execute(o);
		if(r.isSuccess())return new ScannerAvailability(scanner,ScannerAvailabilityStatus.AVAILABLE,first(r.getOutput()),null);
		String error=(r.getError()==null?"":r.getError()); ScannerAvailabilityStatus s=r.getExitCode()==-1&&error.toLowerCase().contains("no such file")?ScannerAvailabilityStatus.UNAVAILABLE:ScannerAvailabilityStatus.ERROR;
		return new ScannerAvailability(scanner,s,null,error.isBlank()?"Scanner unavailable":error);
	} private String first(String v){return v==null?null:v.strip().lines().findFirst().orElse(null);}
}
