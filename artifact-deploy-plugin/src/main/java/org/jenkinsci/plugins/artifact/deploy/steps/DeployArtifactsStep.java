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
package org.jenkinsci.plugins.artifact.deploy.steps;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeployConfigurationProvider;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
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

public class DeployArtifactsStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Map<String, Serializable> options = new ConcurrentHashMap<>();
	private final Map<String, Serializable> metadata = new ConcurrentHashMap<>();

	private String repo = null;

	/**
	 * Comma- or space-separated list of patterns of files/directories to be
	 * archived.
	 */
	private String artifacts = null;

	private String bundle = null;

	@DataBoundConstructor
	public DeployArtifactsStep(final String repo) {
		this.repo = StringUtils.trimToNull(repo);
	}

	public String getRepo() {
		return repo;
	}

	public String getArtifacts() {
		return this.artifacts;
	}

	public Map<String, Serializable> getOptions() {
		return this.options;
	}

	public Map<String, Serializable> getMetadata() {
		return this.metadata;
	}

	public String getBundle() {
		return bundle;
	}

	@DataBoundSetter
	public void setArtifacts(final String artifacts) {
		this.artifacts = StringUtils.trimToNull(artifacts);
	}

	@DataBoundSetter
	public void setOptions(final Map<String, Serializable> options) {
		if (options == null)
			this.options.clear();
		else
			this.options.putAll(options);
	}

	@DataBoundSetter
	public void setMetadata(final Map<String, Serializable> metadata) {
		if (metadata == null)
			this.metadata.clear();
		else
			this.metadata.putAll(metadata);
	}

	@DataBoundSetter
	public void setBundle(final String bundle) {
		this.bundle = StringUtils.trimToNull(bundle);
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		final ArtifactRepository repo = ArtifactDeployConfigurationProvider.findRepoByName(context.get(Run.class),
				this.repo);
		if (repo == null)
			return new InvalidStepExecution(context) {
				private static final long serialVersionUID = 1L;

				protected void log(TaskListener listener) {
					listener.error("Repository (%s) not available!", DeployArtifactsStep.this.getRepo());
				}
			};
		final ArtifactDeployExecutionFactory<?> factory = ArtifactDeployExecutionFactory.get(repo.getClass());
		if (factory == null)
			return new InvalidStepExecution(context) {
				private static final long serialVersionUID = 1L;

				@Override
				protected void log(TaskListener listener) {
					listener.fatalError("Repository of (%s) has no valid factory instance!",
							DeployArtifactsStep.this.getRepo());
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
	public static class DeployArtifactDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "deployArtifacts";
		}

		@Override
		public String getDisplayName() {
			return Messages.DeployArtifacts_DisplayName();
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
