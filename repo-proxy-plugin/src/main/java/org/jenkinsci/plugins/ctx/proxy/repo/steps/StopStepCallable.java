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
package org.jenkinsci.plugins.ctx.proxy.repo.steps;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jenkinsci.plugins.ctx.proxy.repo.AgentToControllerCallable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import hudson.model.Result;
import jenkins.model.CauseOfInterruption;

public class StopStepCallable implements AgentToControllerCallable<Void, RuntimeException> {

	private static final long serialVersionUID = 1L;

	protected final String flowNodeId;
	protected final Result result;
	protected final CauseOfInterruption cause;

	public StopStepCallable(final String flowNodeId, final CauseOfInterruption cause) {
		this(flowNodeId, Result.FAILURE, cause);
	}

	public StopStepCallable(final String flowNodeId, final Result result, final CauseOfInterruption cause) {
		this.flowNodeId = flowNodeId;
		this.result = result;
		this.cause = cause;
	}

	@Override
	public Void call() throws RuntimeException {
		final CompletableFuture<Void> done = new CompletableFuture<>();

		StepExecution.acceptAll(exec -> {
			try {
				final String id = exec.getContext()
						.get(FlowNode.class)
						.getId();

				if (StopStepCallable.this.flowNodeId.equals(id)) {
					exec.stop(new FlowInterruptedException(//
							StopStepCallable.this.result, true, StopStepCallable.this.cause));
					done.complete(null);
				}

			} catch (Exception ignore) {
			}
		});

		try {
			done.get(10, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw new IllegalStateException("StepExecution not found for node " + this.flowNodeId);
		}
		return null;
	}
}
