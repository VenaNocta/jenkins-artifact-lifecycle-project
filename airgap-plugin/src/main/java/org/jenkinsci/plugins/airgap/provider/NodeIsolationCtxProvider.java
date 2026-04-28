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
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.airgap.IsolationContext;
import org.jenkinsci.plugins.airgap.IsolationContextProvider;
import org.jenkinsci.plugins.airgap.IsolationContextRunListener;
import org.jenkinsci.plugins.airgap.ctx.LinuxNamespaceContext;
import org.jenkinsci.plugins.variant.OptionalExtension;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

@OptionalExtension(requireClasses = ExecutorStep.class)
public class NodeIsolationCtxProvider implements IsolationContextProvider {

	@Override
	public boolean isApplicable(final StepDescriptor descriptor) {
		return descriptor instanceof ExecutorStep.DescriptorImpl;
	}

	@Override
	public IsolationContext buildIsolationContext(final StepContext context) throws IOException, InterruptedException {
		final Run<?, ?> run = context.get(Run.class);
		final FlowNode flowNode = context.get(FlowNode.class);
		if (run == null || !IsolationContextRunListener.isIsolationForced(run))
			return null;

		final WorkspaceAction ws = flowNode.getAction(WorkspaceAction.class);
		if (ws == null)
			return null;

		final Node node = StringUtils.trimToNull(ws.getNode()) == null ? Jenkins.get()
				: Jenkins.get()
						.getNode(ws.getNode());

		return selectIsolationContext(flowNode, node, context, ws);
	}

	protected IsolationContext selectIsolationContext(final FlowNode flowNode, final Node node,
			final StepContext context, final WorkspaceAction ws) throws IOException, InterruptedException {
		final Run<?, ?> run = context.get(Run.class);
		final TaskListener listener = context.get(TaskListener.class);
		final PrintStream log = listener.getLogger();

		if (isLinux(node, log)) {
			return new LinuxNamespaceContext(run, node, ws, listener);
		}
		return null;
	}

	protected boolean isLinux(final Node node, final PrintStream log) {
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
