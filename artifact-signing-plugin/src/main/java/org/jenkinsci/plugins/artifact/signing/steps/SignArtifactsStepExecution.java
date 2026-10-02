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

import org.jenkinsci.plugins.artifact.signing.config.ArtifactSignerIndex;
import org.jenkinsci.plugins.artifact.signing.config.SignerConfig;
import org.jenkinsci.plugins.artifact.signing.engines.SigningEngine;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.ArtifactManager;

public class SignArtifactsStepExecution extends SynchronousStepExecution<Void> {

	private static final long serialVersionUID = 1L;

	private final SignArtifactsStep step;

	protected SignArtifactsStepExecution(@NonNull final StepContext context, @NonNull final SignArtifactsStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Void run() throws Exception {
		final Run<?, ?> build = getContext().get(Run.class);
		final EnvVars env = getContext().get(EnvVars.class);

		final ArtifactManager artifactManager = build.pickArtifactManager();

		final TaskListener listener = getContext().get(TaskListener.class);

		String artifacts = this.step.getArtifacts();
		if (build instanceof AbstractBuild) { // no expansion in pipelines
			artifacts = env.expand(artifacts);
		}

		final SignerConfig config = ArtifactSignerIndex.getResolver()
				.findEntryByName(build.getParent(), this.step.getSigner());
		if (config == null) {
			listener.getLogger()
					.println(Messages.SignArtifact_Exec_ERR_SignerNotFound(this.step.getSigner()));
			getContext().setResult(Result.FAILURE);
			return null;
		}
		if (!config.isValid(build.getParent())) {
			listener.getLogger()
					.println(Messages.SignArtifact_Exec_ERR_SignerNotValid(this.step.getSigner()));
			getContext().setResult(Result.FAILURE);
			return null;
		}
		final SigningEngine engine = config.createEngine(getContext(), this.step.getSignaturePrefix(),
				this.step.getSignatureSuffix());
		if (engine == null) {
			listener.getLogger()
					.println(Messages.SignArtifact_Exec_ERR_SigningEngineNotFound(this.step.getSigner()));
			getContext().setResult(Result.FAILURE);
			return null;
		}

		if (!engine.signArtifacts(listener, artifactManager, artifacts, this.step.getFingerprint())) {
			getContext().setResult(Result.FAILURE);
			return null;
		}

		listener.getLogger()
				.println(Messages.SignArtifact_Exec_SigningSuccessful());
		return null;
	}

}