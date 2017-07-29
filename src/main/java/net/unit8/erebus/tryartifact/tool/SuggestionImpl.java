package net.unit8.erebus.tryartifact.tool;

import jdk.jshell.SourceCodeAnalysis;

public class SuggestionImpl implements SourceCodeAnalysis.Suggestion {

	private final String continuation;
	private boolean matchesType;

	public SuggestionImpl(String continuation, boolean matchesType) {
		this.continuation = continuation;
		this.matchesType = matchesType;
	}
	@Override
	public String continuation() {
		return continuation;
	}

	@Override
	public boolean matchesType() {
		return matchesType;
	}
}
