/*
 * Copyright © 2025 VenaNocta (venanocta@gmail.com)
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
package org.jenkinsci.plugins.artifact.deploy.steps;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.deploy.context.BundleContext;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;

public class BundleArtifactsStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Map<String, Serializable> metadata = new ConcurrentHashMap<>();

	private String bundle = null;
	/**
	 * Comma- or space-separated list of patterns of files/directories to be
	 * archived.
	 */
	private String artifacts = null;

	@DataBoundConstructor
	public BundleArtifactsStep(final String artifacts) {
		this.artifacts = StringUtils.trimToNull(artifacts);
	}

	public String getBundle() {
		return this.bundle == null ? BundleContext.DEFAULT_BUNDLE_ID : this.bundle;
	}

	public String getArtifacts() {
		return this.artifacts;
	}

	public Map<String, Serializable> getMetadata() {
		return this.metadata;
	}

	@DataBoundSetter
	public void setBundle(final String bundleId) {
		this.bundle = StringUtils.trimToNull(bundleId);
	}

	@DataBoundSetter
	public void setMetadata(final Map<String, Serializable> metadata) {
		if (metadata == null)
			this.metadata.clear();
		else
			this.metadata.putAll(metadata);
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new BundleArtifactsStepExecution(context, this);
	}

	@Extension
	public static class BundleArtifactDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "bundleArtifacts";
		}

		@Override
		public String getDisplayName() {
			return Messages.BundleArtifacts_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(Run.class, EnvVars.class, TaskListener.class, BundleContext.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return Set.of();
		}
	}
}
