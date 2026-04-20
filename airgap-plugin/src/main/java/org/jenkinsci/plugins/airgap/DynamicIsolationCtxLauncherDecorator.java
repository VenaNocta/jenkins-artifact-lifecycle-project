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
package org.jenkinsci.plugins.airgap;

import java.io.IOException;

import org.jenkinsci.plugins.airgap.steps.DoXStep;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.DynamicContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import hudson.Extension;
import hudson.LauncherDecorator;
import hudson.model.Run;

@Extension
public class DynamicIsolationCtxLauncherDecorator extends DynamicContext.Typed<LauncherDecorator> {

	@Override
	protected Class<LauncherDecorator> type() {
		return LauncherDecorator.class;
	}

	@Override
	protected LauncherDecorator get(final DelegatedContext context) throws IOException, InterruptedException {
		final Run<?, ?> run = context.get(Run.class);
		final FlowNode flowNode = context.get(FlowNode.class);
		// apply overrides if available
		IsolationContextAction action = injectContext(context, run, flowNode);
		if (action == null)
			action = getContextAction(run, flowNode);
		if (action == null)
			return null;
		final IsolationContext isolation = action.getContext();
		if (isolation == null)
			return null;
		final LauncherDecorator decorator = isolation.launcherDecorator();
		if (decorator == null)
			return null;
		return BodyInvoker.mergeLauncherDecorators(context.get(LauncherDecorator.class), decorator);
	}

	protected IsolationContextAction getContextAction(final Run<?, ?> run, final FlowNode flowNode) {
		IsolationContextAction action = findAction(flowNode);
		if (action != null)
			return action;
		action = run.getAction(IsolationContextAction.class);
		if (action != null)
			return action;
		return null;
	}

	protected IsolationContextAction injectContext(final DelegatedContext context, final Run<?, ?> run,
			final FlowNode flowNode) throws IOException, InterruptedException {
		final IsolationContext isolation = contextOverride(context, run, flowNode);
		if (isolation == null)
			return null;
		final IsolationContextAction action = new IsolationContextAction(isolation);
		flowNode.addAction(action);
		return action;
	}

	protected IsolationContext contextOverride(final DelegatedContext context, final Run<?, ?> run,
			final FlowNode flowNode) throws IOException, InterruptedException {
		// TODO check via ExtensionPoints if another IsolationContext has to be
		// injected!
		// EX: if Step=="withDockerContainer" -> NOOP or Adapter for Docker!

		if (context.get(StepDescriptor.class) instanceof DoXStep.SignArtifactDescriptor) {
			return new NoOpIsolationContext();
		}

		return null;
	}

	public static IsolationContextAction findAction(final FlowNode flowNode) {
		IsolationContextAction action = flowNode.getAction(IsolationContextAction.class);
		if (action != null)
			return action;

		for (BlockStartNode node : flowNode.iterateEnclosingBlocks()) {
			action = node.getAction(IsolationContextAction.class);
			if (action != null)
				return action;
		}
		return null;
	}
}
