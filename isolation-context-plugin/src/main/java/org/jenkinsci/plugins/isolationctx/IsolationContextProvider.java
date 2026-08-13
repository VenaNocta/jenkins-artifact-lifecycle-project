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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

public interface IsolationContextProvider extends ExtensionPoint {

	public boolean isApplicable(StepDescriptor descriptor);

	public IsolationContextBuilder createBuilder(StepContext context) throws IOException, InterruptedException;

	public static ExtensionList<IsolationContextProvider> all() {
		return ExtensionList.lookup(IsolationContextProvider.class);
	}

	public static interface IsolationContextBuilder extends Serializable {
		public IsolationContext build(FlowNode flowNode) throws IOException, InterruptedException;

		public static StepContext findContext(final FlowNode flowNode)
				throws InterruptedException, CompletionException {
			final String flowNodeId = flowNode.getId();
			final AtomicReference<StepContext> ref = new AtomicReference<>(null);

			try {
				StepExecution.acceptAll(exec -> {
					if (ref.get() == null)
						return;
					try {
						final StepContext stepContext = exec.getContext();
						final String id = stepContext.get(FlowNode.class)
								.getId();

						if (flowNodeId.equals(id))
							ref.set(stepContext);
					} catch (Exception ignore) {
					}
				})
						.get();
			} catch (ExecutionException e) {
				throw new CompletionException(e.getCause());
			}
			return ref.get();
		}
	}
}
