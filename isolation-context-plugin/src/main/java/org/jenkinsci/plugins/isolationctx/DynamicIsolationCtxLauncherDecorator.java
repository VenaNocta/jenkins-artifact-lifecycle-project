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
package org.jenkinsci.plugins.isolationctx;

import java.io.IOException;
import java.io.Serializable;

import org.jenkinsci.plugins.isolationctx.config.IsolationContextAction;
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationProperty;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.DynamicContext;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.Run;

@Extension
public class DynamicIsolationCtxLauncherDecorator extends DynamicContext.Typed<LauncherDecorator> {

	public static final String ERR_REQUIRED_ISOLATION = "Node requires Sandbox-Isolation to be active! - No Sandbox-Isolation available!";

	@Override
	protected Class<LauncherDecorator> type() {
		return LauncherDecorator.class;
	}

	@Override
	protected LauncherDecorator get(final DelegatedContext context) throws IOException, InterruptedException {
		final Node node = context.get(Node.class);
		final Run<?, ?> run = context.get(Run.class);
		final FlowNode flowNode = context.get(FlowNode.class);
		// define fallback result
		LauncherDecorator fallbackResult = null;
		if (node != null) {
			final NodeIsolationProperty isoProp = node.getNodeProperty(NodeIsolationProperty.class);
			if (isoProp != null && !isoProp.isDisabled()) {
				fallbackResult = new AbortingLauncherDecorator(ERR_REQUIRED_ISOLATION);
			}
		}
		// apply overrides if available
		final IsolationContextAction action = WorkflowUtils.findContextAction(IsolationContextAction.class, run,
				flowNode);
		if (action == null)
			return fallbackResult;
		final IsolationContext isolation = action.getContext();
		if (isolation == null)
			return fallbackResult;
		final LauncherDecorator decorator = isolation.launcherDecorator();
		if (decorator == null)
			return fallbackResult;
		return BodyInvoker.mergeLauncherDecorators(context.get(LauncherDecorator.class), decorator);
	}

	public static final class AbortingLauncherDecorator extends LauncherDecorator implements Serializable {

		private static final long serialVersionUID = 1L;

		private final String message;

		public AbortingLauncherDecorator(final String message) {
			this.message = message;
		}

		@Override
		public Launcher decorate(final Launcher launcher, final Node node) {
			return new Launcher.DecoratedLauncher(launcher) {
				@Override
				public Proc launch(final ProcStarter starter) throws IOException {
					throw new AbortException(AbortingLauncherDecorator.this.message);
				}
			};
		}
	}
}
