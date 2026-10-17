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
package org.jenkinsci.plugins.artifact.lifecycle.example;

import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Extension;
import hudson.model.TaskListener;

public class HelloWorldStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public HelloWorldStep() {
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new Execution(context);
	}

	public static final class Execution extends StepExecution {

		private static final long serialVersionUID = 1L;

		Execution(final StepContext context) {
			super(context);
		}

		@Override
		public boolean start() throws Exception {
			getContext().get(TaskListener.class)
					.getLogger()
					.println("Hello World");
			return true;
		}
	}

	@Extension
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "helloWorld";
		}

		@Override
		public String getDisplayName() {
			return "Hello World";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(TaskListener.class);
		}
	}
}
