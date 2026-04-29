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
package org.jenkinsci.plugins.ctx.isolation.steps;

import java.io.Serializable;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.model.Node;

public class DoXStepExecution extends StepExecution {

	private static final long serialVersionUID = 1L;

	private final DoXStep step;

	protected DoXStepExecution(@NonNull StepContext context, @NonNull DoXStep step) {
		super(context);
		this.step = step;
	}

	protected static class Decorator extends LauncherDecorator implements Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public Launcher decorate(Launcher launcher, Node node) {
			// TODO Auto-generated method stub
			return launcher;
		}
	}

	@Override
	public boolean start() throws Exception {
		getContext().newBodyInvoker()
				.withContext(
						BodyInvoker.mergeLauncherDecorators(getContext().get(LauncherDecorator.class), new Decorator()))
				.withCallback(BodyExecutionCallback.wrap(getContext()))
				.start();
		return false;
	}
}