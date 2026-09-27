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
package org.jenkinsci.plugins.artifact.signing.engines;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;

import org.jenkinsci.plugins.artifact.signing.config.ArtifactSignerIndex;
import org.jenkinsci.plugins.artifact.signing.steps.Messages;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;

public abstract class ASigningEngine implements SigningEngine {

	private static final long serialVersionUID = 1L;

	protected final StepContext context;
	protected final String sigPrefix;
	protected final String sigSuffix;

	protected ASigningEngine(final StepContext context, final String signaturePrefix, final String signatureSuffix) {
		this.context = context;
		this.sigPrefix = signaturePrefix;
		this.sigSuffix = signatureSuffix;
	}

	protected StepContext getContext() {
		return this.context;
	}

	protected String getSignaturePrefix() {
		return this.sigPrefix;
	}

	protected String getSignatureSuffix() {
		return this.sigSuffix;
	}

	protected Node getSecureNode() throws IOException, InterruptedException {
		final Node buildNode = getContext().get(Node.class);
		final Label label = Label.get(getLabelExpression());
		if (label != null && label.contains(buildNode))
			return buildNode;
		return Jenkins.get();
	}

	protected FilePath getSecureWorkspace(final Node node, final Run<?, ?> run)
			throws IOException, InterruptedException {
		FilePath ws = null;
		// handle Run instances with dynamic workspaces
		if (run instanceof AbstractBuild<?, ?> build) {
			ws = build.getWorkspace();
		} else if (run instanceof WorkflowRun) {
			final WorkflowRun pipeline = (WorkflowRun) run;
			if (pipeline.getParent() instanceof TopLevelItem) {
				final TopLevelItem item = (TopLevelItem) pipeline.getParent();
				ws = node.getWorkspaceFor(item);
				if (ws == null) {
					throw new IOException(Messages.ASigningEngine_ERR_NodeOffline(node.getDisplayName()));
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
		return ArtifactSignerIndex.get()
				.getSecureLabelExpression();
	}

	protected String getSecureWorkspaceSuffix() {
		return ArtifactSignerIndex.get()
				.getSecureWorkspaceSuffix();
	}

	protected String getFilePathSuffix() {
		return System.getProperty(WorkspaceList.class.getName(), "@");
	}

	protected static String fullPath(final VirtualFile root, final VirtualFile file) {
		return fullPath(root, file, "", "");
	}

	protected static String fullPath(final VirtualFile root, final VirtualFile file, final String prefix,
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
