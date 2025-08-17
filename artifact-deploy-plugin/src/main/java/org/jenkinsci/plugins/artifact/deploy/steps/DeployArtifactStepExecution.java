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

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;

import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeploymentRepository;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;

public abstract class DeployArtifactStepExecution<T extends ArtifactDeploymentRepository> extends StepExecution {

	private static final long serialVersionUID = 1L;

	protected final DeployArtifactStep step;
	protected final T repo;

	protected DeployArtifactStepExecution(@NonNull StepContext context, @NonNull DeployArtifactStep step,
			@NonNull T repo) {
		super(context);
		this.step = step;
		this.repo = repo;
	}

	protected Run<?, ?> getRun() throws IOException, InterruptedException {
		return getContext().get(Run.class);
	}

	protected EnvVars getEnv() throws IOException, InterruptedException {
		return getContext().get(EnvVars.class);
	}

	protected TaskListener getTaskListener() throws IOException, InterruptedException {
		return getContext().get(TaskListener.class);
	}

	protected T getRepo() {
		return this.repo;
	}

	protected String getArtifacts() throws IOException, InterruptedException {
		String artifacts = this.step.getArtifacts();
		if (getRun() instanceof AbstractBuild) { // no expansion in pipelines
			artifacts = getEnv().expand(artifacts);
		}
		return artifacts;
	}

	protected Void run() throws Exception {
		final Node buildNode = getContext().get(Node.class);

		final Run<?, ?> build = getContext().get(Run.class);
		final EnvVars env = getContext().get(EnvVars.class);

		final ArtifactManager artifactManager = build.pickArtifactManager();
		final VirtualFile archiveFileRoot = artifactManager.root();

		final TaskListener listener = getContext().get(TaskListener.class);

		return null;
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file) {
		return fullPath(root, file, "", "");
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file, final String prefix,
			final String suffix) {
		final Deque<String> path = new LinkedList<>();
		path.add(String.format("%s%s.%s", prefix, file.getName(), suffix));
		for (VirtualFile f = file.getParent(); f != null; f = f.getParent()) {
			if (root.equals(f))
				break;
			path.addFirst(f.getName());
		}
		return String.join("/", path);
	}

}