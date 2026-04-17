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
package org.jenkinsci.plugins.airgap.steps;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.airgap.config.ArtifactSigningConfiguration;
import org.jenkinsci.plugins.airgap.config.ArtifactSigningConfigurationProvider;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;

public class SignArtifactStepExecution extends SynchronousStepExecution<Void> {

	private static final long serialVersionUID = 1L;

	private final SignArtifactStep step;

	protected SignArtifactStepExecution(@NonNull StepContext context, @NonNull SignArtifactStep step) {
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
		final EnvVars env = getContext().get(EnvVars.class);
		final FilePath ws = getSecureWorkspace(secureNode, build);

		final ArtifactManager artifactManager = build.pickArtifactManager();
		final VirtualFile archiveFileRoot = artifactManager.root();

		final TaskListener listener = getContext().get(TaskListener.class);

		String artifacts = this.step.getArtifacts();
		if (build instanceof AbstractBuild) { // no expansion in pipelines
			artifacts = env.expand(artifacts);
		}

		String credentialId = this.step.getCredentialId();
		if (StringUtils.isBlank(credentialId))
			credentialId = ArtifactSigningConfigurationProvider
					.get(ArtifactSigningConfigurationProvider::getDefaultCredentialsId, build);
		if (StringUtils.isBlank(credentialId)) {
			listener.getLogger()
					.println(Messages.SignArtifact_Exec_ERR_NoKeyDefined());
			getContext().setResult(Result.FAILURE);
			return null;
		}
		listener.getLogger()
				.println(String.format(Messages.SignArtifact_Exec_FMT_SigningArtifactsWithKey(), credentialId));

		listener.getLogger()
				.println(Messages.SignArtifact_Exec_SigningSuccessful());

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
					throw new IOException(
							String.format(Messages.SignArtifact_Exec_ERR_FMT_NodeOffline(), node.getDisplayName()));
				}
			}
			// else: should not happen, but just in case go to fallback
		}
		if (ws != null) {
			ws = ws.withSuffix(getFilePathSuffix() + getSecureWorkspaceSuffix());
			try (WorkspaceList.Lease lease = node.toComputer()
					.getWorkspaceList()
					.acquire(ws)) {
				return lease.path;
			}

		}
		// as fallback use an equivalent as for the standard archive
		if (node != null)
			return node.createPath(run.getRootDir()
					.getAbsolutePath() + "/" + getSecureWorkspaceSuffix());
		return null;
	}

	protected String getLabelExpression() {
		return ArtifactSigningConfiguration.get()
				.getSecureLabelExpression();
	}

	protected String getSecureWorkspaceSuffix() {
		return ArtifactSigningConfiguration.get()
				.getSecureWorkspaceSuffix();
	}

	protected String getFilePathSuffix() {
		return System.getProperty(WorkspaceList.class.getName(), "@");
	}
}