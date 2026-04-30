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
package org.jenkinsci.plugins.ctx.isolation.ctx;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.ctx.isolation.IsolationContext;
import org.jenkinsci.plugins.ctx.isolation.config.NodeIsolationRuntimeAction;
import org.jenkinsci.plugins.ctx.isolation.ctx.net.LnxNetLink;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

import com.google.common.io.Files;

import hudson.AbortException;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;

public class LinuxNamespaceContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	public static final String TEMPL_LINE = "[ Linux-Namespace » %d ] %s";

	protected transient final Object linkLock = new Object();

	protected transient final DecoratorFactory factory;
	protected transient final Run<?, ?> run;
	protected transient final Node node;
	protected transient final WorkspaceAction ws;
	protected transient final TaskListener listener;
	protected transient final FilePath envCachePath;

	protected long processId = -1;

	protected transient final SortedMap<Integer, LnxNetLink> _activeLinks = new TreeMap<>();
	protected transient CompletableFuture<LnxIdentity> _lnxIdentityFuture = null;

	public LinuxNamespaceContext(final Run<?, ?> run, final Node node, final WorkspaceAction ws,
			final TaskListener listener) {
		this.factory = new DecoratorFactory(this);
		this.run = run;
		this.node = node;
		this.ws = ws;
		this.listener = listener;
		this.envCachePath = ws.getWorkspace()
				.withSuffix("@env_cache");
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

		try {
			// get user
			lst[0] = actInContext(new ExecRemoteProcess(Arrays.asList("id", "-r", "--user")))
					.thenApply(LinuxNamespaceContext::outputToString)
					.thenAccept(id::setUser);
			// get group
			lst[1] = actInContext(new ExecRemoteProcess(Arrays.asList("id", "-r", "--group")))
					.thenApply(LinuxNamespaceContext::outputToString)
					.thenAccept(id::setGroup);
			// get groups
			lst[2] = actInContext(new ExecRemoteProcess(Arrays.asList("id", "-r", "--groups")))
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

	protected NodeIsolationRuntimeAction getNodeRuntime() {
		try {
			return NodeIsolationRuntimeAction.get(this.node);
		} catch (IOException ignored) {
			ignored.printStackTrace();
		}
		return null;
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

	protected List<String> nsenterNetCmd(final long processId) {
		final List<String> cmd = new ArrayList<>();

		// sudo nsenter --target=75008 --net --
		cmd.add("sudo");
		cmd.add("nsenter");
		cmd.add("--target=" + processId);
		cmd.add("--net");
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
			this.ws.getWorkspace()
					.mkdirs();
			this.envCachePath.mkdirs();

			ExecRemoteProcess remoteProcess = new ExecRemoteProcess(cmd);
			CompletableFuture<Process> future = this.ws.getWorkspace()
					.act(remoteProcess);
			this.processId = remoteProcess.processId();
			future.thenAccept(p -> logProcessException(p, 137));

		} catch (IOException | InterruptedException e) {
			log.println("[Linux-Namespace] Failed to start isolated environment!");
			e.printStackTrace(log);
			return;
		}

		try {
			final List<String> netCmd = nsenterNetCmd(this.processId);

			netCmd.add("ip");
			netCmd.add("link");
			netCmd.add("set");
			netCmd.add("lo");
			netCmd.add("up");

			actInContext(new ExecRemoteProcess(netCmd).await(true)).thenAccept(this::logProcessException);

		} catch (Throwable e) {
			log.println(String.format(TEMPL_LINE, this.processId, "Network Setup Failed!"));
			e.printStackTrace(log);
			return;
		}

		// LnxNetLink link = connectNetLink();
		// log.println("Run Subnet: " + link.getHostIfaceName() + " - "
		// + link.getSubnetV4().toCidrNotation());

		log.println(String.format(TEMPL_LINE, this.processId, "Started isolated environment"));
	}

	@Override
	public void destroy() {
		if (this.processId == -1)
			return;

		final PrintStream log = this.listener.getLogger();

		// Kill process tree
		try {
			final ExecRemoteProcess remoteProcess = new ExecRemoteProcess(
					Arrays.asList("kill", "-KILL", "-" + this.processId));
			this.ws.getWorkspace()
					.act(remoteProcess.await(true));
			this.envCachePath.deleteRecursive();

			log.println(String.format(TEMPL_LINE, this.processId, "Stopped isolated environment"));
			this.processId = -1;
		} catch (InterruptedException | IOException e) {
			log.println(String.format(TEMPL_LINE, this.processId, "Failed to stop isolated environment!"));
			e.printStackTrace(log);
		}

		// Cleanup active LnxLinks
		final NodeIsolationRuntimeAction nodeRuntime = getNodeRuntime();
		if (nodeRuntime != null) {
			for (LnxNetLink link : this._activeLinks.values()) {
				// Remove veth interfaces
				disconnectNetLink(link);
			}
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

	@Override
	public UnixDomainSocketAddress connectUnixSocket(final String path) {
		final FilePath wsPath = this.ws.getWorkspace();
		// TODO use FilePath of overlay mount!
		return UnixDomainSocketAddress.of(wsPath.child(path)
				.getRemote());
	}

	@Override
	public LnxNetLink connectNetLink() {
		final NodeIsolationRuntimeAction nodeRuntime = getNodeRuntime();
		if (nodeRuntime == null)
			return null;

		synchronized (this.linkLock) {
			int linkNum = this._activeLinks.isEmpty() ? 0 : this._activeLinks.lastKey() + 1;

			final String hostIfaceName = String.format("veth-h%d-%d", this.processId, linkNum);
			final String contextIfaceName = String.format("veth-n%d-%d", this.processId, linkNum);

			final IpNet4Address subnet4 = nodeRuntime.reserveIpv4Subnet(this.run);
			Ip4Address hostAddr4 = null, contextAddr4 = null;
			if (subnet4 != null) {
				hostAddr4 = subnet4.getLowestAddress()
						.increment();
				contextAddr4 = hostAddr4.increment();
			}

			final IpNet6Address subnet6 = nodeRuntime.reserveIpv6Subnet(this.run);
			Ip6Address hostAddr6 = null, contextAddr6 = null;
			if (subnet6 != null) {
				hostAddr6 = subnet6.getLowestAddress()
						.increment();
				contextAddr6 = hostAddr6.increment();
			}

			final LnxNetLink link = new LnxNetLink(hostIfaceName, contextIfaceName, //
					subnet4, hostAddr4, contextAddr4, //
					subnet6, hostAddr6, contextAddr6);
			this._activeLinks.put(linkNum, link);

			try {

				actInContext(new ExecRemoteProcess(//
						Arrays.asList("sudo", "ip", "link", "add", hostIfaceName, "type", "veth", "peer", "name",
								contextIfaceName)//
				).await(true)).thenAccept(this::logProcessException);
				actInContext(new ExecRemoteProcess(//
						Arrays.asList("sudo", "ip", "link", "set", contextIfaceName, "netns",
								Long.toString(this.processId))//
				).await(true)).thenAccept(this::logProcessException);
				if (subnet4 != null) {
					actInContext(new ExecRemoteProcess(//
							Arrays.asList("sudo", "ip", "addr", "add", link.getHostAddressV4()
									.toCidrNotation() + "/30", "dev", hostIfaceName)//
					).await(true)).thenAccept(this::logProcessException);
				}
				if (subnet6 != null) {
					actInContext(new ExecRemoteProcess(//
							Arrays.asList("sudo", "ip", "addr", "add", link.getHostAddressV6()
									.toCidrNotation() + "/126", "dev", hostIfaceName)//
					).await(true)).thenAccept(this::logProcessException);
				}
				actInContext(new ExecRemoteProcess(//
						Arrays.asList("sudo", "ip", "link", "set", hostIfaceName, "up")//
				).await(true)).thenAccept(this::logProcessException);
				List<String> cmd;
				if (subnet4 != null) {
					cmd = nsenterNetCmd(this.processId);
					cmd.addAll(Arrays.asList("ip", "addr", "add", link.getContextAddressV4()
							.toCidrNotation() + "/30", "dev", contextIfaceName));
					actInContext(new ExecRemoteProcess(cmd).await(true)).thenAccept(this::logProcessException);
				}
				if (subnet6 != null) {
					cmd = nsenterNetCmd(this.processId);
					cmd.addAll(Arrays.asList("ip", "addr", "add", link.getContextAddressV6()
							.toCidrNotation() + "/126", "dev", contextIfaceName));
					actInContext(new ExecRemoteProcess(cmd).await(true)).thenAccept(this::logProcessException);
				}
				cmd = nsenterNetCmd(this.processId);
				cmd.addAll(Arrays.asList("ip", "link", "set", contextIfaceName, "up"));
				actInContext(new ExecRemoteProcess(cmd).await(true)).thenAccept(this::logProcessException);

			} catch (InterruptedException | IOException e) {
				final PrintStream log = this.listener.getLogger();
				log.println(String.format(TEMPL_LINE, this.processId,
						"Failed to connect host-only veth-link to isolated environment!"));
				e.printStackTrace(log);

				disconnectNetLink(link);
				return null;
			}
			return link;
		}
	}

	@Override
	public void disconnectNetLink(final NetLink anyLink) {
		if (!(anyLink instanceof LnxNetLink))
			return;
		final LnxNetLink link = (LnxNetLink) anyLink;
		final PrintStream log = this.listener.getLogger();
		final NodeIsolationRuntimeAction nodeRuntime = getNodeRuntime();

		synchronized (this.linkLock) {
			if (!this._activeLinks.containsValue(link))
				return;

			try {
				actInContext(new ExecRemoteProcess(//
						Arrays.asList("sudo", "ip", "link", "del", link.getHostIfaceName())//
				).await(true));
			} catch (IOException | InterruptedException e) {
				e.printStackTrace(log);
			}

			if (nodeRuntime != null) {
				nodeRuntime.freeIpv4Subnet(link.getSubnetV4());
				nodeRuntime.freeIpv6Subnet(link.getSubnetV6());
			}
		}
	}

	protected void logProcessException(final Process process) {
		logProcessException(process, 0);
	}

	protected void logProcessException(final Process process, final int expectedExitCode) {
		final PrintStream log = this.listener.getLogger();
		final int exitCode = process.exitValue();
		if (exitCode == expectedExitCode)
			return;
		log.println("exitCode=" + exitCode);
		try {
			process.getInputStream()
					.transferTo(log);
		} catch (IOException e) {
			e.printStackTrace(log);
		}
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

		private final LinuxNamespaceContext ctx;

		protected DecoratorFactory(final LinuxNamespaceContext lnxContext) {
			this.ctx = lnxContext;
		}

		@Override
		public Launcher decorate(final Launcher launcher, final Node node) {
			return new Launcher.DecoratedLauncher(launcher) {
				@Override
				public Proc launch(final ProcStarter starter) throws IOException {
					if (DecoratorFactory.this.ctx.processId == -1)
						throw new AbortException("[Linux-Namespace] Namespace unavailable!");

					final Map<String, String> env = Arrays.asList(starter.envs())
							.stream()
							.map(s -> s.split("=", 2))
							// Filter out bash functions
							.filter(arr -> !arr[0].endsWith("%%"))
							.collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));

					// Wrap command with nsenter
					final List<String> cmd;
					final String envFileName;
					try {
						cmd = DecoratorFactory.this.ctx.nsenterCmd();
						envFileName = DecoratorFactory.this.ctx.envCachePath.act(new WriteEnvFile(env));
					} catch (Throwable e) {
						if (e instanceof RuntimeException runE)
							throw runE;
						throw new RuntimeException(e);
					}

					cmd.addAll(Arrays.asList("sh", "-c", "(. " + envFileName + "; exec \"$@\")"));

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
					// .println("============= \n" + String.join("\n", cmd));

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

	public static class WriteEnvFile extends MasterToSlaveFileCallable<String> {

		private static final long serialVersionUID = 1L;

		protected final Map<String, String> env;

		public WriteEnvFile(final Map<String, String> env) {
			this.env = env;
		}

		@Override
		public String invoke(final File wsDir, final VirtualChannel channel) throws IOException, InterruptedException {
			final File envFile = File.createTempFile("cmd-", ".env", wsDir);

			final StringBuilder builder = new StringBuilder();
			for (Entry<String, String> entry : this.env.entrySet())
				builder.append("export " + entry.getKey() + "=" + singleQuote(entry.getValue()) + "\n");

			Files.write(builder.toString()
					.getBytes(), envFile);

			return envFile.getAbsolutePath();
		}

		public static String singleQuote(final String value) {
			if (value == null) {
				return "''";
			}
			// replace ' with '\'' (close quote, escaped quote, reopen)
			return "'" + value.replace("'", "'\"'\"'") + "'";
		}
	}
}
