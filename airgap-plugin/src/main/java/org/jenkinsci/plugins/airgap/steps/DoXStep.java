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
package org.jenkinsci.plugins.airgap.steps;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

import org.jenkinsci.plugins.airgap.IsolationContext;
import org.jenkinsci.plugins.airgap.IsolationContextProvider;
import org.jenkinsci.plugins.airgap.ctx.NoOpIsolationContext;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class DoXStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public DoXStep() {
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new DoXStepExecution(context, this);
	}

	@Extension
	public static class SignArtifactDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "doX";
		}

		@Override
		public String getDisplayName() {
			return Messages.SignArtifact_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Node.class, Run.class, EnvVars.class, TaskListener.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return ImmutableSet.of();
		}

		@Override
		public boolean takesImplicitBlockArgument() {
			return true;
		}

		@Override
		public boolean isAdvanced() {
			return true;
		}
	}

	@Extension
	public static class Provider implements IsolationContextProvider {

		@Override
		public boolean isApplicable(final StepDescriptor descriptor) {
			return descriptor instanceof SignArtifactDescriptor;
		}

		@Override
		public IsolationContext buildIsolationContext(final StepContext context)
				throws IOException, InterruptedException {
			return new NoOpIsolationContext();
		}
	}
}
