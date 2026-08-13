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
package org.jenkinsci.plugins.isolationctx.hooks;

import java.io.IOException;
import org.jenkinsci.plugins.isolationctx.IsolationContextProvider;
import org.jenkinsci.plugins.isolationctx.IsolationContextProvider.IsolationContextBuilder;
import org.jenkinsci.plugins.isolationctx.dto.IsolationContextAction;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import hudson.Extension;

@Extension
public class IsolationContextStepListener implements StepListener {

	@Override
	public void notifyOfNewStep(final Step step, final StepContext context) {
		try {
			onNewContextBlock(step, context);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected void onNewContextBlock(final Step step, final StepContext context)
			throws IOException, InterruptedException {
		final FlowNode flowNode = context.get(FlowNode.class);
		final StepDescriptor stepDescriptor = step.getDescriptor();

		if (stepDescriptor == null || flowNode.getAction(IsolationContextAction.class) != null)
			return;

		final IsolationContextBuilder builder = contextOverride(stepDescriptor, flowNode, context);
		if (builder != null) {
			flowNode.addAction(new IsolationContextAction(flowNode, builder));
		}
		return;
	}

	protected IsolationContextBuilder contextOverride(final StepDescriptor descriptor, final FlowNode flowNode,
			final StepContext context) throws IOException, InterruptedException {

		IsolationContextBuilder builder = null;
		for (IsolationContextProvider provider : IsolationContextProvider.all()) {
			if (provider.isApplicable(descriptor)) {
				builder = provider.createBuilder(context);
				if (builder != null) {
					return builder;
				}
			}
		}
		return null;
	}
}
