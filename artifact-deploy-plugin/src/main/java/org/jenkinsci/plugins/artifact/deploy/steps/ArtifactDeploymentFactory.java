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

import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeploymentRepository;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

public abstract class ArtifactDeploymentFactory<T extends ArtifactDeploymentRepository> implements ExtensionPoint {
	public final transient Class<? extends T> clazz;

	protected ArtifactDeploymentFactory(final Class<? extends T> clazz) {
		this.clazz = clazz;
	}

	protected ArtifactDeploymentFactory() {
		this.clazz = (Class<T>) getClass().getEnclosingClass();
		if (this.clazz == null)
			throw new AssertionError(getClass()
					+ " doesn't have an outer class. Use the constructor that takes the Class object explicitly.");
	}

	public abstract StepExecution createDeployStepExecution(final StepContext context, final DeployArtifactStep step,
			final T repo) throws Exception;

	public StepExecution newDeployStepExecution(final StepContext context, final DeployArtifactStep step,
			final ArtifactDeploymentRepository repo) throws Exception {
		return createDeployStepExecution(context, step, (T) repo);
	}

	@CheckForNull
	public static <C extends ArtifactDeploymentRepository> ArtifactDeploymentFactory<C> get(final Class<C> type) {
		for (ArtifactDeploymentFactory<C> f : Jenkins.get()
				.getExtensionList(ArtifactDeploymentFactory.class))
			if (f.clazz == type)
				return f;
		return null;
	}

}
