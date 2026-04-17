package org.jenkinsci.plugins.airgap;

import hudson.model.Run;
import jenkins.model.RunAction2;

public class SandboxAction implements RunAction2 {

	private transient Run<?, ?> run;

	private final SandboxInstance sandbox;

	public SandboxAction(SandboxInstance sandbox) {
		this.sandbox = sandbox;
	}

	public SandboxInstance getSandbox() {
		return sandbox;
	}

	@Override
	public String getIconFileName() {
		return null;
	}

	@Override
	public String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return null;
	}

	@Override
	public void onAttached(Run<?, ?> run) {
		this.run = run;
	}

	@Override
	public void onLoad(Run<?, ?> run) {
		this.run = run;
	}
}
