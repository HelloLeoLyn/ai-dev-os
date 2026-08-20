package com.aidevos.orchestrator.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based classification of a natural language goal into deterministic
 * step intents. No NLP framework: simple, stable, explainable keyword rules.
 *
 * The goal only proposes steps; the backend still enforces the tool allowlist,
 * workspace boundary and execution limits at validation/execution time.
 * Anything unrecognized falls back to a single AI_STEP.
 */
public final class NaturalGoalClassifier {

	public enum Kind {
		AI,
		TOOL,
		HUMAN_GATE
	}

	public record StepIntent(Kind kind, String toolName, String command) {
	}

	private record Candidate(String keyword, Kind kind, String toolName, String command,
			boolean wordBoundary) {
	}

	private record Matched(StepIntent intent, int index) {
	}

	private static final List<Candidate> CANDIDATES = List.of(
		new Candidate("git status", Kind.TOOL, "git", "status", false),
		new Candidate("git diff", Kind.TOOL, "git", "diff", false),
		new Candidate("git log", Kind.TOOL, "git", "log", false),
		new Candidate("编译", Kind.TOOL, "maven", "compile", false),
		new Candidate("compile", Kind.TOOL, "maven", "compile", true),
		new Candidate("单元测试", Kind.TOOL, "maven", "test", false),
		new Candidate("unit test", Kind.TOOL, "maven", "test", false),
		new Candidate("测试", Kind.TOOL, "maven", "test", false),
		new Candidate("test", Kind.TOOL, "maven", "test", true),
		new Candidate("npm build", Kind.TOOL, "npm", "build", false),
		new Candidate("npm test", Kind.TOOL, "npm", "test", false),
		new Candidate("前端构建", Kind.TOOL, "npm", "build", false),
		new Candidate("前端 build", Kind.TOOL, "npm", "build", false),
		new Candidate("build 前端", Kind.TOOL, "npm", "build", false),
		new Candidate("health", Kind.TOOL, "http", "health", true),
		new Candidate("readiness", Kind.TOOL, "http", "health", true),
		new Candidate("健康检查", Kind.TOOL, "http", "health", false),
		new Candidate("人工确认", Kind.HUMAN_GATE, null, null, false),
		new Candidate("人工审核", Kind.HUMAN_GATE, null, null, false),
		new Candidate("审批", Kind.HUMAN_GATE, null, null, false),
		new Candidate("review", Kind.HUMAN_GATE, null, null, true),
		new Candidate("等待人工", Kind.HUMAN_GATE, null, null, false));

	private NaturalGoalClassifier() {
	}

	/**
	 * Returns the ordered step intents for a natural language goal. An AI code
	 * step comes first whenever the goal implies code changes, followed by any
	 * recognized deterministic tool steps and human gates in occurrence order.
	 * Duplicate tool intents are emitted once.
	 */
	public static List<StepIntent> classify(String goal) {
		if (goal == null || goal.isBlank()) {
			return List.of();
		}
		String text = goal.toLowerCase();
		List<StepIntent> intents = new ArrayList<>();
		if (isCodeChange(text)) {
			intents.add(new StepIntent(Kind.AI, null, null));
		}
		List<Matched> matches = new ArrayList<>();
		for (Candidate candidate : CANDIDATES) {
			int index = indexOf(text, candidate);
			if (index >= 0) {
				matches.add(new Matched(new StepIntent(candidate.kind(), candidate.toolName(),
					candidate.command()), index));
			}
		}
		matches.sort(Comparator.comparingInt(Matched::index));
		Set<StepIntent> seen = new HashSet<>();
		for (Matched matched : matches) {
			if (seen.add(matched.intent())) {
				intents.add(matched.intent());
			}
		}
		return intents;
	}

	private static boolean isCodeChange(String text) {
		return contains(text, "修改") || contains(text, "实现") || contains(text, "修复")
			|| contains(text, "重构") || contains(text, "开发") || contains(text, "bug")
			|| containsWord(text, "fix") || containsWord(text, "implement")
			|| containsWord(text, "refactor") || containsWord(text, "change")
			|| containsWord(text, "modify");
	}

	private static int indexOf(String text, Candidate candidate) {
		if (candidate.wordBoundary()) {
			Pattern pattern = Pattern.compile("(?<![a-z])" + Pattern.quote(candidate.keyword())
				+ "(?![a-z])");
			var matcher = pattern.matcher(text);
			return matcher.find() ? matcher.start() : -1;
		}
		return text.indexOf(candidate.keyword());
	}

	private static boolean contains(String text, String keyword) {
		return text.contains(keyword);
	}

	private static boolean containsWord(String text, String word) {
		return Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])")
			.matcher(text).find();
	}
}
