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
package org.jenkinsci.plugins.ctx.isolation.hooks;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.jenkinsci.plugins.ctx.isolation.IsolationContext;
import org.jenkinsci.plugins.ctx.isolation.IsolationContextAction;
import org.jenkinsci.plugins.ctx.isolation.IsolationContextProvider;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;

@OptionalExtension(requireClasses = { CpsFlowExecution.class, StepStartNode.class, StepEndNode.class })
public class IsolationContextFlowListener extends FlowExecutionListener {

	@Override
	public void onRunning(final FlowExecution execution) {
		execution.addListener(new GraphListener.Synchronous() {
			@Override
			public void onNewHead(final FlowNode node) {
				IsolationContextFlowListener.this.onNewStepStartNode(node);
				IsolationContextFlowListener.this.onNewStepEndNode(node);
			}
		});
	}

	protected void onNewStepStartNode(final FlowNode flowNode) {
		if (!(flowNode instanceof StepStartNode))
			return;

		final StepStartNode start = (StepStartNode) flowNode;
		final StepDescriptor stepDescriptor = start.getDescriptor();
		final FlowExecution flowExec = start.getExecution();

		if (stepDescriptor == null //
				|| !(flowExec instanceof CpsFlowExecution) //
				|| flowNode.getAction(IsolationContextAction.class) != null)
			return;

		final CpsFlowExecution cpsFlowExec = (CpsFlowExecution) flowExec;
		final ListenableFuture<List<StepExecution>> future = cpsFlowExec.getCurrentExecutions(true);

		future.addListener(new Runnable() {
			@Override
			public void run() {
				if (future.isCancelled())
					return;
				try {
					for (StepExecution stepExecution : future.get()) {
						if (stepExecution == null)
							continue;
						final StepContext stepContext = stepExecution.getContext();
						if (flowNode == stepContext.get(FlowNode.class)) {

							final IsolationContext isolation = contextOverride(stepDescriptor, flowNode, stepContext);
							if (isolation != null) {
								flowNode.addAction(new IsolationContextAction(isolation));
								isolation.setup();
							}
							return;
						}
					}
				} catch (InterruptedException | ExecutionException | IOException e) {
					e.printStackTrace();
				}
			}
		}, MoreExecutors.directExecutor());
	}

	protected IsolationContext contextOverride(final StepDescriptor descriptor, final FlowNode flowNode,
			final StepContext context) throws IOException, InterruptedException {

		IsolationContext isolation = null;
		for (IsolationContextProvider provider : IsolationContextProvider.all()) {
			if (provider.isApplicable(descriptor)) {
				isolation = provider.buildIsolationContext(context);
				if (isolation != null)
					return isolation;
			}
		}
		return null;
	}

	protected void onNewStepEndNode(final FlowNode flowNode) {
		if (!(flowNode instanceof StepEndNode))
			return;

		final StepEndNode end = (StepEndNode) flowNode;
		final StepDescriptor stepDescriptor = end.getDescriptor();
		final FlowExecution flowExec = end.getExecution();
		final IsolationContextAction action = end.getStartNode()
				.getAction(IsolationContextAction.class);

		if (stepDescriptor == null //
				|| !(flowExec instanceof CpsFlowExecution) //
				|| action == null)
			return;

		final IsolationContext isolation = action.getContext();
		if (isolation != null)
			isolation.destroy();
	}
}
