/*
 * Copyright © 2026 VenaNocta (venanocta@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.artifact.signing.steps;

import java.io.Serializable;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class SignArtifactStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Comma- or space-separated list of patterns of files/directories to be
	 * archived.
	 */
	private String artifacts = null;

	private String credentialId = null;

	private String prefix = null;
	private String suffix = null;

	private boolean fingerprint = true;

	@DataBoundConstructor
	public SignArtifactStep(String artifacts) {
		if (artifacts != null)
			this.artifacts = artifacts.trim();
	}

	public String getArtifacts() {
		return this.artifacts;
	}

	public String getCredentialId() {
		return this.credentialId;
	}

	public boolean getFingerprint() {
		return this.fingerprint;
	}

	public String getSignaturePrefix() {
		if (StringUtils.isBlank(this.prefix))
			return "";
		return this.prefix;
	}

	public String getSignatureSuffix() {
		if (StringUtils.isBlank(this.suffix))
			return "asc";
		return this.suffix;
	}

	@DataBoundSetter
	public void setCredentialId(String credentialId) {
		this.credentialId = credentialId;
	}

	@DataBoundSetter
	public void setSignaturePrefix(String prefix) {
		this.prefix = prefix;
	}

	@DataBoundSetter
	public void setSignatureSuffix(String suffix) {
		this.suffix = suffix;
	}

	@DataBoundSetter
	public void setFingerprint(boolean fingerprint) {
		this.fingerprint = fingerprint;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new SignArtifactStepExecution(context, this);
	}

	@Extension
	public static class SignArtifactDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "signArtifacts";
		}

		@Override
		public String getDisplayName() {
			return Messages.SignArtifact_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Node.class, Run.class, EnvVars.class, TaskListener.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return ImmutableSet.of();
		}
	}
}
