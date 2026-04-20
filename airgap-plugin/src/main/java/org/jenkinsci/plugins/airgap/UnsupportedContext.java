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
package org.jenkinsci.plugins.airgap;

import java.io.IOException;
import java.io.Serializable;

import hudson.AbortException;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class UnsupportedContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	protected DecoratorFactory factory;

	public UnsupportedContext() {
		this.factory = new DecoratorFactory();
	}

	@Override
	public LauncherDecorator launcherDecorator() {
		return this.factory;
	}

	@Override
	public void setup(Run<?, ?> run, TaskListener listener) {
	}

	@Override
	public void destroy(Run<?, ?> run, TaskListener listener) {
	}

	protected static class DecoratorFactory extends LauncherDecorator implements Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public Launcher decorate(Launcher launcher, Node node) {
			return new Launcher.DecoratedLauncher(launcher) {
				@Override
				public Proc launch(ProcStarter starter) throws IOException {
					throw new AbortException("Forced Sandbox-Isolation active! - Current Sandbox not isolated!");
				}
			};
		}
	}
}
