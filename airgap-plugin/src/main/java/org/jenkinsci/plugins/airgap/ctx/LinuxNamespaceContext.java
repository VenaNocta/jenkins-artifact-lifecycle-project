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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.jenkinsci.plugins.airgap.IsolationContext;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

public class LinuxNamespaceContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	protected transient final DecoratorFactory factory;
	protected transient final Node node;
	protected transient final WorkspaceAction ws;
	protected transient final TaskListener listener;

	protected long processId = -1;

	protected transient CompletableFuture<LnxIdentity> _lnxIdentityFuture = null;

	public LinuxNamespaceContext(final Node node, final WorkspaceAction ws, final TaskListener listener) {
		this.factory = new DecoratorFactory();
		this.node = node;
		this.ws = ws;
		this.listener = listener;
	}

	@Override
	public LauncherDecorator launcherDecorator() {
		return this.factory;
	}

	public synchronized CompletableFuture<LnxIdentity> lnxIdentityFuture() {
		if (this._lnxIdentityFuture != null)
			return this._lnxIdentityFuture;

		final CompletableFuture<LnxIdentity> future = new CompletableFuture<>();
		final LnxIdentity id = new LnxIdentity();

		final CompletableFuture<?>[] lst = new CompletableFuture[3];

		// get user
		try {
			lst[0] = this.ws.getWorkspace()
					.act(new ExecRemoteProcess(Arrays.asList("id", "-r", "--user")))
					.thenApply(LinuxNamespaceContext::outputToString)
					.thenAccept(id::setUser);
			// get group
			lst[1] = this.ws.getWorkspace()
					.act(new ExecRemoteProcess(Arrays.asList("id", "-r", "--group")))
					.thenApply(LinuxNamespaceContext::outputToString)
					.thenAccept(id::setGroup);
			// get groups
			lst[2] = this.ws.getWorkspace()
					.act(new ExecRemoteProcess(Arrays.asList("id", "-r", "--groups")))
					.thenApply(LinuxNamespaceContext::outputToString)
					.thenApply(s -> s.split(" "))
					.thenAccept(id::setGroups);
		} catch (IOException | InterruptedException e) {
			future.completeExceptionally(e);
		}

		CompletableFuture.allOf(lst)
				.handle((result, error) -> {
					if (error != null)
						future.completeExceptionally(error);
					future.complete(id);
					return null;
				});

		return this._lnxIdentityFuture = future;
	}

	public LnxIdentity lnxIdentity() throws Throwable {
		try {
			return lnxIdentityFuture().get();
		} catch (ExecutionException e) {
			throw e.getCause();
		}
	}

	public List<String> nsenterCmd() throws Throwable {
		final List<String> cmd = new ArrayList<>();

		final LnxIdentity lnxIdentity = lnxIdentity();

		// sudo nsenter --target=75008 --mount --uts --ipc --net --pid --
		cmd.add("sudo");
		cmd.add("nsenter");
		cmd.add("--target=" + LinuxNamespaceContext.this.processId);
		cmd.add("--mount");
		cmd.add("--uts");
		cmd.add("--ipc");
		cmd.add("--net");
		cmd.add("--pid");
		cmd.add("--wd");
		cmd.add("--");
		// setpriv --reuid=<uid> --regid=<gid> --groups=<groups> --inh-caps=-all
		// --reset-env
		// --
		cmd.add("setpriv");
		cmd.add("--reuid=" + lnxIdentity.getUser());
		cmd.add("--regid=" + lnxIdentity.getGroup());
		cmd.add("--groups=" + String.join(",", lnxIdentity.getGroups()));
		cmd.add("--inh-caps=-all");
		cmd.add("--reset-env");
		cmd.add("--");

		return cmd;
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
		// cmd.add("--user");
		// cmd.add("--map-root-user");
		cmd.add("--propagation=private");
		cmd.add("--wd=" + this.ws.getPath());
		cmd.add("--");

		// Keep namespace alive
		cmd.add("/usr/bin/bash");
		cmd.add("-c");
		// //log.print(String.join(" ", cmd));
		cmd.add("exec tail -f /dev/null");
		// ip link set lo up &&

		// //log.println(" \"exec tail -f /dev/null\"\n");

		try {
			ExecRemoteProcess remoteProcess = new ExecRemoteProcess(cmd);
			CompletableFuture<Process> future = this.ws.getWorkspace()
					.act(remoteProcess);
			this.processId = remoteProcess.processId();
			future.thenAccept(p -> {
				int exitCode = p.exitValue();
				if (exitCode == 137)
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

	@Override
	public <T> T actInContext(final FileCallable<T> callable) throws IOException, InterruptedException {
		return this.ws.getWorkspace()
				.act(callable);
	}

	@Override
	public <T> Future<T> actInContextAsync(final FileCallable<T> callable) throws IOException, InterruptedException {
		return this.ws.getWorkspace()
				.actAsync(callable);
	}

	protected static String outputToString(final Process process) {
		try {
			return new String(process.getInputStream()
					.readAllBytes()).trim();
		} catch (IOException e) {
			throw new CompletionException(e);
		}
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

					// Wrap command with nsenter
					final List<String> cmd;
					try {
						cmd = LinuxNamespaceContext.this.nsenterCmd();
					} catch (Throwable e) {
						if (e instanceof RuntimeException runE)
							throw runE;
						throw new RuntimeException(e);
					}

					// recalculate mask: based on nsenter cmd prefix
					boolean[] oldMasks = starter.masks();
					int oldSize = oldMasks == null ? 0 : oldMasks.length;
					int cmdSize = cmd.size();
					boolean[] masks = new boolean[cmdSize + oldSize];
					for (int i = 0; i < cmdSize; i++)
						masks[i] = false;
					for (int i = 0; i < oldSize; i++)
						masks[cmdSize + i] = oldMasks[i];
					starter.masks(masks);

					// merge: nsenter with actual cmd

					cmd.addAll(starter.cmds());

					starter.cmds(cmd);

					// launcher.getListener()
					// .getLogger()
					// .println("======== " + String.join(" ", cmd));

					return super.launch(starter);
				}
			};
		}
	}

	public static class LnxIdentity {

		protected String user = "";
		protected String group = "";
		protected String[] groups = new String[0];

		public String getUser() {
			return this.user;
		}

		public String getGroup() {
			return this.group;
		}

		public String[] getGroups() {
			return this.groups;
		}

		public void setUser(final String user) {
			this.user = user;
		}

		public void setGroup(final String group) {
			this.group = group;
		}

		public void setGroups(final String[] groups) {
			this.groups = groups;
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
