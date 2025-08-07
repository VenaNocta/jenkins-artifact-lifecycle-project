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
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.http.client.methods.HttpGet;
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
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import io.jenkins.plugins.httpclient.RobustHTTPClient;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;

public class SignArtifactStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Comma- or space-separated list of patterns of files/directories to be
	 * archived.
	 */
	private String artifacts = null;

	private boolean fingerprint;

	@DataBoundConstructor
	public SignArtifactStep(String artifacts) {
		if (artifacts != null)
			this.artifacts = artifacts.trim();
	}

	public String getArtifacts() {
		return this.artifacts;
	}

	public boolean getFingerprint() {
		return this.fingerprint;
	}

	@DataBoundSetter
	public void setFingerprint(boolean fingerprint) {
		this.fingerprint = fingerprint;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new SignArtifactStepExecution(context, this);
	}

	public static class SignArtifactStepExecution extends SynchronousStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		private SignArtifactStep step;

		protected SignArtifactStepExecution(StepContext context, SignArtifactStep step) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			final Node buildNode = getContext().get(Node.class);
			final Label label = Label.get(getLabelExpression());
			final Node secureNode;
			if (label.contains(buildNode)) {
				secureNode = buildNode;
			} else {
				secureNode = Jenkins.get();
			}

			final Run<?, ?> build = getContext().get(Run.class);
			final FilePath ws = getSecureWorkspace(secureNode, build);

			final ArtifactManager artifactManager = build.pickArtifactManager();
			final VirtualFile archiveFileRoot = artifactManager.root();

			TaskListener listener = getContext().get(TaskListener.class);

			listener.getLogger()
					.println("SecWorkspace: " + ws.getRemote());
			System.out.println("SecWorkspace: " + ws.getRemote());

			final RobustHTTPClient httpClient = new RobustHTTPClient();

			for (String filePath : archiveFileRoot.list(this.step.getArtifacts(), null, true)) {
				final VirtualFile archivedArtifactFile = archiveFileRoot.child(filePath);

				if (!archivedArtifactFile.isFile())
					continue;

				final FilePath artifactSignature = ws.child(fullPath(archiveFileRoot, archivedArtifactFile))
						.withSuffix(".asc");
				final URL url = archivedArtifactFile.toExternalURL();

				// example call ... well that should be done on the 'secureNode'
				if (url == null) {
					try (InputStream dataStream = archivedArtifactFile.open()) {
						signArtifactFromStream(dataStream, artifactSignature);
					}
				} else {
					httpClient.connect("download",
							"download " + RobustHTTPClient.sanitize(url) + " to " + secureNode.getDisplayName()
									+ " for signing",
							client -> client.execute(new HttpGet(url.toString())), response -> {
								try (InputStream is = response.getEntity()
										.getContent()) {
									signArtifactFromStream(is, artifactSignature);
								}
							}, listener);
				}
			}
			return null;
		}

		protected String fullPath(final VirtualFile root, final VirtualFile file) {
			Deque<String> path = new LinkedList<>();
			for (VirtualFile f = file; f != null; f = f.getParent()) {
				if (root.equals(f))
					break;
				path.addFirst(f.getName());
			}
			return String.join("/", path);
		}

		protected void signArtifactFromStream(final InputStream inputStream, final FilePath targetFile) {

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

		private String getLabelExpression() {
			return "secure";
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
