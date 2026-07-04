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
package org.jenkinsci.plugins.isolationctx.provider;

import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.isolationctx.IsolationContext;
import org.jenkinsci.plugins.isolationctx.IsolationContextProvider;
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationProperty;
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationRuntimeAction;
import org.jenkinsci.plugins.isolationctx.ctx.LinuxNodeNamespaceContext;
import org.jenkinsci.plugins.isolationctx.handler.LnxNodeIsolationHandler;
import org.jenkinsci.plugins.isolationctx.handler.NodeIsolationHandler;
import org.jenkinsci.plugins.isolationctx.hooks.IsolationContextRunListener;
import org.jenkinsci.plugins.variant.OptionalExtension;

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

		final WorkspaceAction ws = flowNode.getAction(WorkspaceAction.class);
		if (ws == null)
			return null;

		final Node node = StringUtils.trimToNull(ws.getNode()) == null ? Jenkins.get()
				: Jenkins.get()
						.getNode(ws.getNode());

		// bypass context isolation if allowed
		if (!IsolationContextRunListener.isIsolationForced(run) && NodeIsolationProperty.isDisabled(node))
			return null;

		return selectIsolationContext(flowNode, node, context, ws);
	}

	protected IsolationContext selectIsolationContext(final FlowNode flowNode, final Node node,
			final StepContext context, final WorkspaceAction ws) throws IOException, InterruptedException {
		final NodeIsolationHandler isolationHandler;
		{
			final NodeIsolationRuntimeAction isolationRuntime = NodeIsolationRuntimeAction.get(node);
			isolationHandler = isolationRuntime == null ? null : isolationRuntime.isolationHandler();
		}

		final Run<?, ?> run = context.get(Run.class);
		final TaskListener listener = context.get(TaskListener.class);

		if (isolationHandler.isActive() == false) {
			listener.error("Context Isolation currently not available on this Node!");
			return null;
		}

		if (isolationHandler instanceof LnxNodeIsolationHandler) {
			return new LinuxNodeNamespaceContext(run, node, ws, listener);
		}
		return null;
	}
}
