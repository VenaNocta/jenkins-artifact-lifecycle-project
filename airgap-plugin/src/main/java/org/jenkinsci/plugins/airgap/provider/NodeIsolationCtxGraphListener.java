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
package org.jenkinsci.plugins.airgap.provider;

import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import java.io.IOException;

import org.jenkinsci.plugins.airgap.IsolationContext;
import org.jenkinsci.plugins.airgap.IsolationContextAction;
import org.jenkinsci.plugins.airgap.IsolationContextRunListener;
import org.jenkinsci.plugins.airgap.LinuxNamespaceContext;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import jenkins.model.Jenkins;

@Extension
public class NodeIsolationCtxGraphListener implements GraphListener {

	@Override
	public void onNewHead(final FlowNode flowNode) {
		final Run<?, ?> run = getRun(flowNode);
		if (!IsolationContextRunListener.isIsolationForced(run))
			return;

		final WorkspaceAction ws = flowNode.getAction(WorkspaceAction.class);
		if (ws == null)
			return;

		final Node node = Jenkins.get()
				.getNode(ws.getNode());

		final IsolationContext isolation = selectIsolationContext(flowNode, node, ws);
		if (isolation == null)
			return;

		final IsolationContextAction action = new IsolationContextAction(isolation);

		flowNode.addAction(action);
		// TODO consider on-demand setup -> start only when needed!
		isolation.setup(run, null);
	}

	protected static Run<?, ?> getRun(final FlowNode flowNode) {
		try {
			final FlowExecutionOwner owner = flowNode.getExecution()
					.getOwner();
			return (Run<?, ?>) owner.getExecutable();
		} catch (Exception ignored) {
			return null;
		}
	}

	protected IsolationContext selectIsolationContext(final FlowNode flowNode, final Node node,
			final WorkspaceAction ws) {
		if (isLinux(node)) {
			return new LinuxNamespaceContext();
		}
		return null;
	}

	protected boolean isLinux(final Node node) {
		if (node == null)
			return false;
		final Computer computer = node.toComputer();
		if (computer == null)
			return false;

		try {
			final Object value = computer.getSystemProperties()
					.get("os.name");
			if (value instanceof String os) {
				return os.toLowerCase()
						.contains("linux");
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return false;
	}
}
