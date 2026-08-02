package com.aidevos.orchestrator.planner;

import java.util.Objects;

import com.aidevos.orchestrator.planner.replan.ReplanRequest;

public final class FakePlanner implements Planner {

	private final String name;
	private final PlanDraft draft;
	private final RuntimeException failure;

	public FakePlanner(String name, PlanDraft draft) {
		this(name, Objects.requireNonNull(draft, "draft"), null);
	}

	private FakePlanner(String name, PlanDraft draft, RuntimeException failure) {
		this.name = Objects.requireNonNull(name, "name");
		this.draft = draft;
		this.failure = failure;
	}

	public static FakePlanner failing(String name, RuntimeException failure) {
		return new FakePlanner(name, null, Objects.requireNonNull(failure, "failure"));
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public PlanDraft plan(PlanningRequest request) {
		if (failure != null) {
			throw failure;
		}
		return draft;
	}

	@Override
	public PlanDraft replan(ReplanRequest request) {
		return plan(null);
	}
}
