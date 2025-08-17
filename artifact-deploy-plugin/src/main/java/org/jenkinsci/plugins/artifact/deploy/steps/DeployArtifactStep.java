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
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeployConfigurationProvider;
import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeploymentRepository;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class DeployArtifactStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	private String repoId = null;

	/**
	 * Comma- or space-separated list of patterns of files/directories to be
	 * archived.
	 */
	private String artifacts = null;

	private String bundle = null;

	@DataBoundConstructor
	public DeployArtifactStep(String repo) {
		this.repoId = StringUtils.trimToNull(repo);
	}

	public String getRepoId() {
		return repoId;
	}

	public String getArtifacts() {
		return this.artifacts;
	}

	public String getBundle() {
		return bundle;
	}

	@DataBoundSetter
	public void setArtifacts(String artifacts) {
		this.artifacts = artifacts;
	}

	@DataBoundSetter
	public void setBundle(String bundle) {
		this.bundle = bundle;
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		final ArtifactDeploymentRepository repo = ArtifactDeployConfigurationProvider.findRepo(context.get(Run.class),
				this.repoId);
		if (repo == null)
			return new InvalidStepExecution(context) {
				private static final long serialVersionUID = 1L;

				protected void log(TaskListener listener) {
					listener.error("Repository (%s) not available!", DeployArtifactStep.this.getRepoId());
				}
			};
		final ArtifactDeploymentFactory<?> factory = ArtifactDeploymentFactory.get(repo.getClass());
		if (factory == null)
			return new InvalidStepExecution(context) {
				private static final long serialVersionUID = 1L;

				@Override
				protected void log(TaskListener listener) {
					listener.fatalError("Repository of (%s) has no valid factory instance!",
							DeployArtifactStep.this.getRepoId());
				}
			};
		return factory.newDeployStepExecution(context, this, repo);
	}

	public abstract static class InvalidStepExecution extends StepExecution {

		private static final long serialVersionUID = 1L;

		public InvalidStepExecution(@NonNull final StepContext context) {
			super(context);
		}

		protected abstract void log(final TaskListener listener);

		@Override
		public boolean start() throws Exception {
			log(getContext().get(TaskListener.class));
			getContext().setResult(Result.FAILURE);
			return true;
		}

	}

	@Extension
	public static class SignArtifactDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "deployArtifacts";
		}

		@Override
		public String getDisplayName() {
			return Messages.DeployArtifact_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, EnvVars.class, TaskListener.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return ImmutableSet.of();
		}
	}
}
