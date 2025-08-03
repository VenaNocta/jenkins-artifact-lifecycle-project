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
package org.jenkinsci.plugins.artifact.signing.steps;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;
import lombok.Getter;

public class SignArtifactStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Comma- or space-separated list of patterns of files/directories to be
	 * archived.
	 */
	@Getter
	private String artifacts = null;

	@Getter
	private boolean fingerprint;

	@DataBoundConstructor
	public SignArtifactStep(String artifacts) {
		if (artifacts != null)
			this.artifacts = artifacts.trim();
	}

	@DataBoundSetter
	public void setFingerprint(boolean fingerprint) {
		this.fingerprint = fingerprint;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new SignArtifactExecution(context, this);
	}

	public static class SignArtifactExecution extends SynchronousStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		private SignArtifactStep step;

		protected SignArtifactExecution(StepContext context, SignArtifactStep step) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			final ArtifactManager artifactManager = getContext().get(Run.class)
					.pickArtifactManager();
			final VirtualFile archiveFileRoot = artifactManager.root();

			TaskListener listener = getContext().get(TaskListener.class);
			Run<?, ?> build = getContext().get(Run.class);

			FilePath ws = getSecureWorkspace(getContext().get(Node.class), build);
			listener.getLogger()
					.println("SecWorkspace: " + ws.getRemote());
			System.out.println("SecWorkspace: " + ws.getRemote());

			return null;
		}

		protected FilePath getSecureWorkspace(final Node node, final Run<?, ?> run)
				throws IOException, InterruptedException {
			FilePath ws = null;
			// handle Run instances with dynamic workspaces
			if (run instanceof AbstractBuild<?, ?>) {
				final AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
				ws = build.getWorkspace();
			}
			if (run instanceof WorkflowRun) {
				final WorkflowRun pipeline = (WorkflowRun) run;
				if (pipeline.getParent() instanceof TopLevelItem) {
					ws = node.getWorkspaceFor((TopLevelItem) pipeline.getParent());
					if (ws == null) {
						throw new IOException(node.getDisplayName() + " may be offline");
					}
				}
				// else: should not happen, but just in case go to fallback
			}
			if (ws != null) {
				ws = ws.withSuffix(getFilePathSuffix() + "secure");
				try (WorkspaceList.Lease lease = node.toComputer()
						.getWorkspaceList()
						.acquire(ws)) {
					return lease.path;
				}

			}
			// as fallback use an equivalent as for the standard archive
			if (node != null)
				return node.createPath(run.getRootDir()
						.getAbsolutePath() + "/secure");
			return null;
		}

		private String getFilePathSuffix() {
			return System.getProperty(WorkspaceList.class.getName(), "@");
		}
	}

	@Extension
	public static class SignArtifactDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "signArtifacts";
		}

		@Override
		public String getDisplayName() {
			return "Sign archived Artifacts";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return ImmutableSet.of();
		}
	}
}
