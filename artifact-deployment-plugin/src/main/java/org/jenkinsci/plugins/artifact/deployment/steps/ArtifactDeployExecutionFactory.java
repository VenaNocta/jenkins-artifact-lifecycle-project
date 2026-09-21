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
package org.jenkinsci.plugins.artifact.deployment.steps;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

public abstract class ArtifactDeployExecutionFactory<T extends ArtifactRepository> implements ExtensionPoint {

	public final transient Class<? extends T> clazz;

	protected ArtifactDeployExecutionFactory(final Class<? extends T> clazz) {
		this.clazz = clazz;
	}

	protected ArtifactDeployExecutionFactory() {
		this.clazz = (Class<T>) getClass().getEnclosingClass();
		if (this.clazz == null)
			throw new AssertionError(getClass()
					+ " doesn't have an outer class. Use the constructor that takes the Class object explicitly.");
	}

	public abstract StepExecution create(final StepContext context, final DeployArtifactsStep step, final T repo)
			throws Exception;

	public StepExecution newDeployStepExecution(final StepContext context, final DeployArtifactsStep step,
			final ArtifactRepository repo) throws Exception {
		return create(context, step, (T) repo);
	}

	@CheckForNull
	public static <C extends ArtifactRepository> ArtifactDeployExecutionFactory<C> get(final Class<C> type) {
		for (ArtifactDeployExecutionFactory<C> f : Jenkins.get()
				.getExtensionList(ArtifactDeployExecutionFactory.class))
			if (f.clazz == type)
				return f;
		return null;
	}
}
