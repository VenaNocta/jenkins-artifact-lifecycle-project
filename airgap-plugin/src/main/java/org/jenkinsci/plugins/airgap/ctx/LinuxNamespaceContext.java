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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jenkinsci.plugins.airgap.IsolationContext;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

import hudson.AbortException;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

public class LinuxNamespaceContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	protected transient final DecoratorFactory factory;
	protected transient final Node node;
	protected transient final WorkspaceAction ws;
	protected transient final TaskListener listener;

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
					if (LinuxNamespaceContext.this.processId == -1)
						throw new AbortException("[Linux-Namespace] Namespace unavailable!");

					launcher.getListener()
							.getLogger()
							.print("-- LinuxNamespaceContext -- " + factory.hashCode() + "\n");

					// Wrap command with nsenter
					List<String> cmd = new ArrayList<>();
					cmd.add("nsenter");
					cmd.add("--target=" + LinuxNamespaceContext.this.processId);
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

					System.out.println("======== " + String.join(" ", cmd));

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

		final PrintStream log = this.listener.getLogger();

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
		cmd.add("--map-root-user");
		cmd.add("--propagation=private");
		cmd.add("--wd=" + this.ws.getPath());
		cmd.add("--");

		// Keep namespace alive
		cmd.add("/usr/bin/bash");
		cmd.add("-c");
		cmd.add("ip link set lo up && exec tail -f /dev/null");

		try {
			ExecRemoteProcess remoteProcess = new ExecRemoteProcess(cmd);
			CompletableFuture<Process> future = this.ws.getWorkspace()
					.act(remoteProcess);
			this.processId = remoteProcess.processId();
			future.thenAccept(p -> {
				int exitCode = p.exitValue();
				if (exitCode == 0)
					return;
				log.println("exitCode=" + exitCode);
				try {
					p.getInputStream()
							.transferTo(log);
				} catch (IOException e) {
					e.printStackTrace(log);
				}
			});
		} catch (IOException | InterruptedException e) {
			log.println("[Linux-Namespace] Failed to start isolated environment!");
			e.printStackTrace(log);
			return;
		}

		log.println("[Linux-Namespace] Started isolated environment [ PID=" + this.processId + " ]");
	}

	@Override
	public void destroy() {
		if (this.processId == -1)
			return;

		final PrintStream log = this.listener.getLogger();

		// Kill process tree
		try {
			ExecRemoteProcess remoteProcess = new ExecRemoteProcess(
					Arrays.asList("kill", "-KILL", "-" + this.processId));
			this.ws.getWorkspace()
					.act(remoteProcess.await(true));

			log.println("[Linux-Namespace] Stopped isolated environment [ PID=" + this.processId + " ]");
			this.processId = -1;
		} catch (InterruptedException | IOException e) {
			log.println("[Linux-Namespace] Failed to stop isolated environment! [ PID=" + this.processId + " ]");
			e.printStackTrace(log);
		}
	}

	public static class ExecRemoteProcess extends MasterToSlaveFileCallable<CompletableFuture<Process>> {

		private static final long serialVersionUID = 1L;

		protected final List<String> cmd;

		protected boolean await = false;
		protected long processId = -1;

		public ExecRemoteProcess(final List<String> cmd) {
			this.cmd = cmd;
		}

		@Override
		public CompletableFuture<Process> invoke(File wsDir, VirtualChannel channel)
				throws IOException, InterruptedException {
			final ProcessBuilder pb = new ProcessBuilder(cmd);

			// final Map<String, String> env = pb.environment();
			// env.clear();
			// env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin");
			// env.put("LANG", "C");
			// env.put("LC_ALL", "C");

			pb.directory(wsDir);
			pb.redirectErrorStream(true);
			final Process process = pb.start();
			this.processId = process.pid();
			if (this.await)
				process.waitFor();
			return process.onExit();
		}

		public long processId() {
			return this.processId;
		}

		public ExecRemoteProcess await(boolean value) {
			this.await = value;
			return this;
		}
	}
}
