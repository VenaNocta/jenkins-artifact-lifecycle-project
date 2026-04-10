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
package org.jenkinsci.plugins.artifact.bundle.steps;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import org.jenkinsci.plugins.artifact.bundle.api.context.Bundle;
import org.jenkinsci.plugins.artifact.bundle.api.context.BundleContext;
import org.jenkinsci.plugins.artifact.bundle.api.context.Bundles;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.ArtifactManager;

public class BundleArtifactsStepExecution extends SynchronousStepExecution<Void> {

	private static final long serialVersionUID = 1L;

	private final BundleArtifactsStep step;

	protected BundleArtifactsStepExecution(@NonNull StepContext context, @NonNull BundleArtifactsStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Void run() throws Exception {
		final Run<?, ?> build = getContext().get(Run.class);
		final EnvVars env = getContext().get(EnvVars.class);
		final Bundle bundle = getContext().get(BundleContext.class)
				.getBundle(this.step.getBundle());

		final ArtifactManager artifactManager = build.pickArtifactManager();

		final PrintStream logger = getContext().get(TaskListener.class)
				.getLogger();

		String artifacts = this.step.getArtifacts();
		if (build instanceof AbstractBuild) { // no expansion in pipelines
			artifacts = env.expand(artifacts);
		}

		final List<String> index = new LinkedList<>();
		Bundles.findArtifacts(artifactManager, artifacts, null, true, (artifactFile, metadata) -> {
			// log the artifact's path
			index.add(Bundles.getMetadataEntry(metadata, String.class, Bundle.METADATA_FILE_PATH));
			// override with specified values
			metadata.putAll(this.step.getMetadata());
			// index artifact
			bundle.indexArtifact(artifactFile, metadata);
		});

		logger.println(String.format(Messages.BundleArtifacts_Exec_FMT_Report(), index.size(), this.step.getBundle()));
		for (String filePath : index) {
			logger.println("  - " + filePath);
		}
		logger.flush();
		return null;
	}

}