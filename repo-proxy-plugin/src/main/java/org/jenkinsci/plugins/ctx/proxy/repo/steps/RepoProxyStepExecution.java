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

import java.io.PrintStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jenkinsci.plugins.ctx.isolation.IsolationContext;
import org.jenkinsci.plugins.ctx.isolation.IsolationContext.NetLink;
import org.jenkinsci.plugins.ctx.isolation.WorkflowUtils;
import org.jenkinsci.plugins.ctx.isolation.config.IsolationContextAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.google.common.util.concurrent.FutureCallback;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import hudson.model.TaskListener;

public class RepoProxyStepExecution extends StepExecution {

	private static final long serialVersionUID = 1L;

	private final RepoProxyStep step;

	protected RepoProxyStepExecution(@NonNull StepContext context, @NonNull RepoProxyStep step) {
		super(context);
		this.step = step;
	}

	@Override
	public boolean start() throws Exception {
		final StepContext context = getContext();
		final TaskListener listener = context.get(TaskListener.class);
		final PrintStream log = listener.getLogger();

		final IsolationContextAction action = WorkflowUtils.findContextAction( //
				context.get(Run.class), context.get(FlowNode.class));
		final IsolationContext isoContext = action == null ? null : action.getContext();
		if (isoContext == null)
			throw new RuntimeException("IsolationContext unavailable!");

		final NetLink link = isoContext.connectNetLink();

		if (link == null)
			throw new RuntimeException("Failed to connect the Proxy to the IsolationContext!");

		log.println("[ RepoProxy ] Started Proxy on " + link.getHostAddressV4()
				.toCidrNotation());

		final Map<String, String> proxyEnv = new LinkedHashMap<>();
		proxyEnv.put("MVN_PROXY_central_URL", String.format("http://%s:%d/%s/", link.getHostAddressV4()
				.toCidrNotation(), 8080, "central"));

		// TODO implement

		getContext().newBodyInvoker()
				.withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class),
						EnvironmentExpander.constant(proxyEnv)))
				.withCallback(new BlockCallback(getContext(), isoContext, link))
				.start();
		return false;
	}

	private static class BlockCallback extends BodyExecutionCallback {

		private static final long serialVersionUID = 1L;

		protected final FutureCallback<Object> v;
		protected final IsolationContext isoContext;
		protected final NetLink link;

		public BlockCallback(final FutureCallback<Object> v, final IsolationContext isoContext, final NetLink link) {
			if (!(v instanceof Serializable))
				throw new IllegalArgumentException(v.getClass() + " is not serializable");
			this.v = v;
			this.isoContext = isoContext;
			this.link = link;
		}

		@Override
		public void onSuccess(final StepContext context, final Object result) {
			v.onSuccess(result);
			free();
		}

		@Override
		public void onFailure(final StepContext context, final Throwable t) {
			v.onFailure(t);
			free();
		}

		protected void free() {
			this.isoContext.disconnectNetLink(this.link);
		}
	}
}