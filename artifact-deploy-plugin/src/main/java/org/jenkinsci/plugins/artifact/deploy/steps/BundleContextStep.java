/*
 * Copyright © 2025 VenaNocta (venanocta@gmail.com)
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
package org.jenkinsci.plugins.artifact.deploy.steps;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

import org.jenkinsci.plugins.artifact.deploy.context.BundleContext;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;

public class BundleContextStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public BundleContextStep() {
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new BundleContextExecution(context);
	}

	public static class BundleContextExecution extends StepExecution {

		private static final long serialVersionUID = 1L;

		private BundleContext context;

		public BundleContextExecution(final StepContext context) {
			super(context);
			this.context = new BundleContext();
		}

		protected BundleContext getBundleContext() throws IOException, InterruptedException {
			return getContext().get(BundleContext.class);
		}

		@Override
		public boolean start() throws Exception {
			getContext().newBodyInvoker()
					.withContext(this.context)
					.withCallback(new MergeContextExecutionCallback(getBundleContext()))
					.start();
			return false;
		}

	}

	private static class MergeContextExecutionCallback extends BodyExecutionCallback {

		private static final long serialVersionUID = 1L;

		private final BundleContext upstream;

		public MergeContextExecutionCallback(final BundleContext upstream) {
			this.upstream = upstream;
		}

		@Override
		public void onSuccess(final StepContext context, final Object resultObject) {
			context.onSuccess(resultObject);
			if (this.upstream == null)
				return;

			try {
				if (context.get(FlowExecution.class) instanceof CpsFlowExecution exec) {
					if (exec.getResult()
							.isWorseThan(Result.UNSTABLE))
						return;
				} else if (context.get(Run.class)
						.getResult()
						.isWorseThan(Result.UNSTABLE))
					return;

				final BundleContext bundleContext = context.get(BundleContext.class);
				if (bundleContext != null)
					this.upstream.merge(bundleContext);
			} catch (IOException | InterruptedException e) {
				context.onFailure(e);
			}
		}

		@Override
		public void onFailure(final StepContext context, final Throwable t) {
			context.onFailure(t);
		}

	}

	@Extension
	public static class BundleContextDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "bundleContext";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return Messages.BundleContext_DisplayName();
		}

		@Override
		public boolean takesImplicitBlockArgument() {
			return true;
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(Run.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return Set.of(BundleContext.class);
		}

	}

}
