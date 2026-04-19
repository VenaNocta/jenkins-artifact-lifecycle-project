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
import java.util.ArrayList;
import java.util.List;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.Run;

public class LinuxNamespaceContext implements IsolationContext {

	protected final Run<?, ?> run;
	protected SandboxInstance sandbox;
	protected DecoratorFactory factory;

	public LinuxNamespaceContext(Run<?, ?> run) {
		this.run = run;
		this.factory = new DecoratorFactory();
	}

	public class DecoratorFactory extends LauncherDecorator {
		@Override
		public Launcher decorate(Launcher launcher, Node node) {
			return new Launcher.DecoratedLauncher(launcher) {
				@Override
				public Proc launch(ProcStarter starter) throws IOException {

					// Wrap command with nsenter
					List<String> cmd = new ArrayList<>();
					cmd.add("nsenter");
					cmd.add("--target=" + sandbox.getPid());
					cmd.add("--mount");
					cmd.add("--net");
					cmd.add("--pid");
					cmd.add("--ipc");
					cmd.add("--uts");
					cmd.add("--");

					cmd.addAll(starter.cmds());

					starter.cmds(cmd);

					return super.launch(starter);
				}
			};
		}
	}

	@Override
	public LauncherDecorator launcherDecorator() {
		return this.factory;
	}
}
