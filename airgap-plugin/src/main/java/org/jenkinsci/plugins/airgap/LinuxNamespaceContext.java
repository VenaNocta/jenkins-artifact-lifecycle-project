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
import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.TaskListener;

public class LinuxNamespaceContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	protected final DecoratorFactory factory;

	protected int processId = -1;

	public LinuxNamespaceContext(final Node node, final WorkspaceAction ws, final TaskListener listener) {
		this.factory = new DecoratorFactory();
	}

	public class DecoratorFactory extends LauncherDecorator implements Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public Launcher decorate(final Launcher launcher, final Node node) {
			return new Launcher.DecoratedLauncher(launcher) {
				@Override
				public Proc launch(final ProcStarter starter) throws IOException {

					launcher.getListener()
							.getLogger()
							.print("-- LinuxNamespaceContext -- " + factory.hashCode() + "\n");

					if (true)
						return super.launch(starter);

					// Wrap command with nsenter
					List<String> cmd = new ArrayList<>();
					cmd.add("nsenter");
					cmd.add("--target=" + processId);
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

	@Override
	public void setup() {
		// TODO Auto-generated method stub
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}
}
