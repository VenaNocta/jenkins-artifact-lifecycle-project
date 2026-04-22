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
package org.jenkinsci.plugins.airgap.ctx;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jenkinsci.plugins.airgap.IsolationContext;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.TaskListener;

public class LinuxNamespaceContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	protected final DecoratorFactory factory;
	protected final Node node;
	protected final WorkspaceAction ws;
	protected final TaskListener listener;

	protected long processId = -1;

	public LinuxNamespaceContext(final Node node, final WorkspaceAction ws, final TaskListener listener) {
		this.factory = new DecoratorFactory();
		this.node = node;
		this.ws = ws;
		this.listener = listener;
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
					cmd.add("--uts");
					cmd.add("--ipc");
					cmd.add("--net");
					cmd.add("--pid");
					cmd.add("--fork");
					cmd.add("--mount-proc");
					cmd.add("--user");
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
		if (this.processId != -1)
			return;

		final PrintStream out = this.listener.getLogger();
		out.println("[Linux-Namespace] starting isolated environment...");

		final List<String> cmd = new ArrayList<>();

		cmd.add("setsid");
		cmd.add("unshare");
		cmd.add("--mount");
		cmd.add("--uts");
		cmd.add("--ipc");
		cmd.add("--net");
		cmd.add("--pid");
		cmd.add("--fork");
		cmd.add("--mount-proc");
		cmd.add("--user");
		// cmd.add("--map-root-user");
		cmd.add("--");

		// Keep namespace alive
		cmd.add("bash");
		cmd.add("-c");
		cmd.add("\"ip link set lo up && tail -f /dev/null\"");

		final ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);

		try {
			final Process process = pb.start();
			this.processId = process.pid();
		} catch (IOException e) {
			out.println("[Linux-Namespace] Failed to start isolated environment!");
			e.printStackTrace(out);
			return;
		}

		out.println("[Linux-Namespace] Started isolated environment [ PID=" + this.processId + " ]");
	}

	@Override
	public void destroy() {
		if (this.processId == -1)
			return;
		if (true)
			return;

		final PrintStream out = this.listener.getLogger();
		out.println("[Linux-Namespace] stopping isolated environment [ PID=" + this.processId + " ]");

		// Kill process tree
		final ProcessBuilder pb = new ProcessBuilder(Arrays.asList("kill", "-KILL", "-" + this.processId));
		try {
			pb.start()
					.waitFor();
		} catch (InterruptedException | IOException e) {
			out.println("[Linux-Namespace] Failed to stop isolated environment!");
			e.printStackTrace(out);
		}

		out.println("[Linux-Namespace] Stopped isolated environment [ PID=" + this.processId + " ]");
	}
}
