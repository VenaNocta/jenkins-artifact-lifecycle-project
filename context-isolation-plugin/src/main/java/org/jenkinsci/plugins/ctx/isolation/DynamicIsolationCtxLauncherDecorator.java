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
package org.jenkinsci.plugins.ctx.isolation;

import java.io.IOException;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.DynamicContext;
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
		final IsolationContextAction action = WorkflowUtils.findContextAction(run, flowNode);
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
}
