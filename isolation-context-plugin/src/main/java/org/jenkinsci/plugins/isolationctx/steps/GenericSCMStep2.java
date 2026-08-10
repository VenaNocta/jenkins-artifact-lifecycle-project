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
package org.jenkinsci.plugins.isolationctx.steps;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.jenkinsci.plugins.isolationctx.config.JobIsolationProperty;
import org.jenkinsci.plugins.isolationctx.ctx.DecoratedIsolationLauncher;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.jenkinsci.plugins.workflow.util.StaplerReferer;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;

/**
 * Runs a user-selected {@link SCM}.
 */
public class GenericSCMStep2 extends SCMStep {

	private final SCM scm;

	@DataBoundConstructor
	public GenericSCMStep2(SCM scm) {
		this.scm = scm;
	}

	public SCM getScm() {
		return scm;
	}

	@Override
	protected SCM createSCM() {
		return scm;
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new StepExecutionImpl(this, context);
	}

	public static final class StepExecutionImpl extends SynchronousNonBlockingStepExecution<Map<String, String>> {

		private transient final GenericSCMStep2 step;

		StepExecutionImpl(final GenericSCMStep2 step, final StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Map<String, String> run() throws Exception {
			final StepContext ctx = getContext();
			final Run<?, ?> run = ctx.get(Run.class);
			final TaskListener listener = ctx.get(TaskListener.class);
			if (!DescriptorImpl.isCeckout2Enabled(run.getParent()))
				throw new StepDisabledException("Unsafe Checkout (step: checkout2) is disabled!");

			final Launcher launcher = ctx.get(Launcher.class);
			final Iterator<DecoratedIsolationLauncher> iterator = DecoratedIsolationLauncher.find(launcher)
					.iterator();
			if (iterator.hasNext()) {
				iterator.next()
						.enableNetworkIsolation(false);
			}

			step.checkout(run, ctx.get(FilePath.class), listener, launcher);
			Map<String, String> envVars = new TreeMap<>();
			step.createSCM()
					.buildEnvironment(run, envVars);
			return envVars;
		}

		private static final long serialVersionUID = 1L;
	}

	@Extension
	public static final class DescriptorImpl extends SCMStepDescriptor {

		@Override
		public String getFunctionName() {
			return "checkout2";
		}

		@Override
		public String getDisplayName() {
			return "Check out from version control";
		}

		public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors() {
			return SCM._for(StaplerReferer.findItemFromRequest(Job.class));
		}

		public static boolean isCeckout2Enabled(final Job<?, ?> job) {
			final JobIsolationProperty prop = job.getProperty(JobIsolationProperty.class);
			return prop != null && prop.isUnsafeCheckoutEnabled();
		}
	}
}
