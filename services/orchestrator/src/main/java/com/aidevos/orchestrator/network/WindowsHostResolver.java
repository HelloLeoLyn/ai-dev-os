package com.aidevos.orchestrator.network;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.aidevos.orchestrator.executor.command.*;
import org.springframework.stereotype.Component;

@Component
public class WindowsHostResolver {
	private static final Pattern DEFAULT_VIA = Pattern.compile("(?:^|\\s)default\\s+via\\s+([^\\s]+)");
	private final CommandExecutor commands;
	public WindowsHostResolver(CommandExecutor commands){this.commands=commands;}
	public String resolve(){
		CommandOptions options=new CommandOptions(); options.setCommand(List.of("ip","route","show","default"));
		options.setTimeout(Duration.ofSeconds(3)); options.setRuntimeNetworkEnabled(false);
		CommandResult result=commands.execute(options);
		String output=result.getOutput()==null?"":result.getOutput();
		String effectiveDefault=output.lines().map(String::trim).filter(v->!v.isBlank()).findFirst().orElse("");
		Matcher matcher=DEFAULT_VIA.matcher(effectiveDefault);
		if(!result.isSuccess()||!matcher.find()) throw new ProxyResolutionException();
		return matcher.group(1);
	}
}
