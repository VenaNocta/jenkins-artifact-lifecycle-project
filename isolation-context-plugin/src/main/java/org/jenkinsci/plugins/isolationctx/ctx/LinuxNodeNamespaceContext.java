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
package org.jenkinsci.plugins.isolationctx.ctx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.isolationctx.IsolationContext;
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationRuntime;
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationRuntimeAction;
import org.jenkinsci.plugins.isolationctx.ctx.net.LnxNetLink;
import org.jenkinsci.plugins.isolationctx.handler.LnxNodeIsolationHandler;
import org.jenkinsci.plugins.isolationctx.handler.LnxNodeIsolationHandler.OverlayMountData;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

import com.google.common.io.Files;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher.ProcStarter;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Future;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.LogTaskListener;
import jenkins.MasterToSlaveFileCallable;
import jenkins.agents.ControllerToAgentFileCallable;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpAddress;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;
import net.runeduniverse.lib.utils.net.api.IpNetAddress;

public class LinuxNodeNamespaceContext implements IsolationContext {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(LinuxNodeNamespaceContext.class.getName());

	public static final String TEMPL_LINE = "[ Linux-Namespace » %d ] %s";

	protected transient final Object linkLock = new Object();

	protected transient final DecoratorFactory factory;
	protected transient final Run<?, ?> run;
	protected transient final Node node;
	protected transient final WorkspaceAction ws;
	protected transient final TaskListener listener;

	protected long processId = -1;
	protected OverlayMountData overlayData = null;

	protected transient final SortedMap<Integer, LnxNetLink> _activeLinks = new TreeMap<>();
	protected transient CompletableFuture<LnxIdentity> _lnxIdentityFuture = null;
	protected transient TaskListener _logListener = null;

	public LinuxNodeNamespaceContext(final Run<?, ?> run, final Node node, final WorkspaceAction ws,
			final TaskListener listener) {
		this.factory = new DecoratorFactory(this);
		this.run = run;
		this.node = node;
		this.ws = ws;
		this.listener = listener;
	}

	// XStream2 hook -> handles unmarshal
	private Object readResolve() {
		final NodeIsolationRuntime runtime = getNodeRuntime();
		if (runtime != null && runtime.isolationHandler() instanceof LnxNodeIsolationHandler handler) {
			// reinit overlayData
			if (this.overlayData != null)
				this.overlayData.initHandler(handler);
		}
		return this;
	}

	@Override
	public LauncherDecorator launcherDecorator() {
		return this.factory;
	}

	protected FilePath envCachePath() {
		return ws.getWorkspace()
				.withSuffix("@env_cache");
	}

	public synchronized CompletableFuture<LnxIdentity> lnxIdentityFuture() {
		if (this._lnxIdentityFuture != null)
			return this._lnxIdentityFuture;

		this._lnxIdentityFuture = new CompletableFuture<>();
		final LnxIdentity id = new LnxIdentity();

		try {
			// get user
			id.setUser(outputToString(wsExecAndJoinCmd(Arrays.asList("id", "-r", "--user"), true)));
			// get group
			id.setGroup(outputToString(wsExecAndJoinCmd(Arrays.asList("id", "-r", "--group"), true)));
			// get groups
			id.setGroups(outputToString(wsExecAndJoinCmd(Arrays.asList("id", "-r", "--groups"), true)).split(" "));

			this._lnxIdentityFuture.complete(id);
		} catch (IOException | InterruptedException e) {
			this._lnxIdentityFuture.completeExceptionally(e);
		}
		return this._lnxIdentityFuture;
	}

	public LnxIdentity lnxIdentity() throws Throwable {
		try {
			return lnxIdentityFuture().get();
		} catch (ExecutionException e) {
			throw e.getCause();
		}
	}

	protected NodeIsolationRuntime getNodeRuntime() {
		try {
			return NodeIsolationRuntimeAction.get(this.node);
		} catch (IOException | InterruptedException ignored) {
			ignored.printStackTrace();
		}
		return null;
	}

	public synchronized TaskListener logListener() {
		if (this._logListener != null)
			return _logListener;
		try {
			return _logListener = getNodeRuntime().isolationHandler()
					.logTaskListener();
		} catch (Throwable e) {
			this._logListener = new LogTaskListener(LOGGER, Level.INFO);
			this._logListener.error("Failed to create Remote-Logger, redirection activated!");
			e.printStackTrace(this._logListener.getLogger());
			return this._logListener;
		}
	}

	public List<String> nsenterCmd() throws Throwable {
		final List<String> cmd = new ArrayList<>();

		final LnxIdentity lnxIdentity = lnxIdentity();

		// sudo nsenter --target=75008 --mount --uts --ipc --net --pid --
		cmd.add("sudo");
		cmd.add("nsenter");
		cmd.add("--target=" + LinuxNodeNamespaceContext.this.processId);
		cmd.add("--mount");
		cmd.add("--uts");
		cmd.add("--ipc");
		cmd.add("--net");
		cmd.add("--pid");
		cmd.add("--wdns=" + this.ws.getWorkspace()
				.absolutize()
				.getRemote());
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

		final PrintStream pipeLog = this.listener.getLogger();
		final PrintStream nodeLog = logListener().getLogger();

		final NodeIsolationRuntime nodeRuntime = getNodeRuntime();

		try {
			final FilePath workspacePath = this.ws.getWorkspace();
			workspacePath.mkdirs();
			final FilePath envCachePath = envCachePath();
			envCachePath.mkdirs();
			final FilePath tmpPath = workspacePath.withSuffix("@tmp");
			tmpPath.mkdirs();

			final StringBuilder scriptBuilder = new StringBuilder();
			scriptBuilder.append("set -e\n\n");
			scriptBuilder.append("/usr/bin/unshare --mount --propagation=slave "
					+ "--uts --ipc --net --pid --fork --mount-proc --wd=" + this.ws.getPath() + " -- ");

			if (nodeRuntime != null && nodeRuntime.isolationHandler() instanceof LnxNodeIsolationHandler handler) {
				final OverlayMountData mountData = handler.overlayCreate(workspacePath.withSuffix("@overlay"));
				// TODO check if there is a WORKSPACE macro to use
				mountData.export("workspace", workspacePath.getParent());
				// ensure paths exist on disk before script is called or root will own them!
				mountData.bind(workspacePath);
				mountData.bind(tmpPath);
				mountData.bind(envCachePath);
				// inject switch_root call to enter overlay into startup cmd
				scriptBuilder.append("switch_root " + mountData.rootPath()
						.absolutize()
						.getRemote() + " ");
				this.overlayData = mountData;
			}

			// Keep namespace alive
			scriptBuilder.append("/usr/bin/bash -c \"exec tail -f /dev/null\"");
			// redirect output to error stream & move to background
			scriptBuilder.append(" 1>&2 &\n");
			// write namespace pid to stdout & await namespace exit
			scriptBuilder.append("\nUNSHARE_PID=$!\nprintf \"%d\\\\n\" $UNSHARE_PID\nwait $UNSHARE_PID\n");

			final FilePath scriptPath = workspacePath.withSuffix("@isoCtxExec.sh");
			scriptPath.delete();
			try (OutputStream stream = scriptPath.write()) {
				stream.write(scriptBuilder.toString()
						.getBytes(StandardCharsets.UTF_8));
			}
			scriptPath.chmod(0500);

			final RemoteProcess2 process2 = this.ws.getWorkspace()
					.act(new ExecRemoteProcess(
							Arrays.asList("sudo", "/usr/bin/bash", "-c", "--", scriptPath.absolutize()
									.getRemote())));
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process2.getIOtriplet().stdout))) {
				this.processId = Long.parseLong(reader.readLine());
				// this.processId = process2.pid(); <-- now would get sudo id = host-namespace!
				// expected exit code, post kill all: 137
			}
		} catch (IOException | InterruptedException e) {
			pipeLog.println("[Linux-Namespace] Failed to start isolated environment!");
			e.printStackTrace(nodeLog);
			return;
		}

		try {
			final List<String> netCmd = nsenterNetCmd(this.processId);

			netCmd.add("ip");
			netCmd.add("link");
			netCmd.add("set");
			netCmd.add("lo");
			netCmd.add("up");

			wsExecAndJoinCmd(netCmd, true);

		} catch (Throwable e) {
			pipeLog.println(String.format(TEMPL_LINE, this.processId, "Network Setup Failed!"));
			e.printStackTrace(nodeLog);
			return;
		}

		// LnxNetLink link = connectNetLink();
		// log.println("Run Subnet: " + link.getHostIfaceName() + " - "
		// + link.getSubnetV4().toCidrNotation());

		pipeLog.println(String.format(TEMPL_LINE, this.processId, "Started isolated environment"));
	}

	@Override
	public void destroy() {
		if (this.processId == -1)
			return;

		final PrintStream pipeLog = this.listener.getLogger();
		final PrintStream nodeLog = logListener().getLogger();

		// Kill process tree
		try {
			// kill process group (may already be dead)
			wsExecAndJoinCmd(Arrays.asList("sudo", "kill", "-KILL", "-" + this.processId), true);
			// kill namespace process
			wsExecAndJoinCmd(Arrays.asList("sudo", "kill", "-KILL", "" + this.processId), true);
			envCachePath().deleteRecursive();

			pipeLog.println(String.format(TEMPL_LINE, this.processId, "Stopped isolated environment"));
			this.processId = -1;
		} catch (InterruptedException | IOException e) {
			pipeLog.println(String.format(TEMPL_LINE, this.processId, "Failed to stop isolated environment!"));
			e.printStackTrace(nodeLog);
		} finally {
			// Cleanup active LnxLinks
			final NodeIsolationRuntime nodeRuntime = getNodeRuntime();
			if (nodeRuntime != null) {
				for (LnxNetLink link : this._activeLinks.values()) {
					try {
						// Remove veth interfaces
						disconnectNetLink(link);
					} catch (IOException | InterruptedException e) {
						e.printStackTrace(nodeLog);
					}
				}
				if (nodeRuntime.isolationHandler() instanceof LnxNodeIsolationHandler handler) {
					try {
						// just try it even if it wasn't created - maybe it was lost before!
						handler.overlayRemove(this.ws.getWorkspace()
								.withSuffix("@overlay"));
					} catch (IOException | InterruptedException e) {
						e.printStackTrace(nodeLog);
					}
				}
			}
		}
	}

	@Override
	public FilePath getWorkspace() {
		return this.ws.getWorkspace();
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
	public boolean mountWorkspacePath(final FilePath workspacePath) throws IOException, InterruptedException {
		if (!workspacePath.exists())
			return false;
		if (this.overlayData == null) {
			// I expect it to just exist
			return true;
		}
		return this.overlayData.bind(workspacePath);
	}

	@Override
	public boolean unmountWorkspacePath(final FilePath workspacePath) throws IOException, InterruptedException {
		if (this.overlayData == null) {
			// I expect it to just exist
			return true;
		}
		return this.overlayData.unbind(workspacePath);
	}

	@Override
	public UnixDomainSocketAddress connectUnixSocket(final String path) {
		final FilePath wsPath = this.ws.getWorkspace();
		// TODO use FilePath of overlay mount!
		return UnixDomainSocketAddress.of(wsPath.child(path)
				.getRemote());
	}

	@Override
	public LnxNetLink connectNetLink() throws IOException, InterruptedException {
		final NodeIsolationRuntime nodeRuntime = getNodeRuntime();
		if (nodeRuntime == null)
			return null;
		final TaskListener nodeLog = logListener();

		synchronized (this.linkLock) {
			int linkNum = this._activeLinks.isEmpty() ? 0 : this._activeLinks.lastKey() + 1;

			final String hostIfaceName = String.format("jciv-h%d-%d", this.processId, linkNum);
			final String contextIfaceName = String.format("jciv-n%d-%d", this.processId, linkNum);

			final Set<IpAddress<?>> hostIpSet = new LinkedHashSet<>();
			final Set<IpNetAddress<?, ?>> subnetSet = new LinkedHashSet<>();
			final IpNet4Address subnet4 = nodeRuntime.reserveIpv4Subnet(this.run, nodeLog);
			Ip4Address hostAddr4 = null, contextAddr4 = null;
			if (subnet4 != null) {
				hostAddr4 = subnet4.getLowestAddress()
						.increment();
				contextAddr4 = hostAddr4.increment();

				hostIpSet.add(hostAddr4);
				subnetSet.add(subnet4);
			}

			final IpNet6Address subnet6 = nodeRuntime.reserveIpv6Subnet(this.run, nodeLog);
			Ip6Address hostAddr6 = null, contextAddr6 = null;
			if (subnet6 != null) {
				hostAddr6 = subnet6.getLowestAddress()
						.increment();
				contextAddr6 = hostAddr6.increment();

				hostIpSet.add(hostAddr6);
				subnetSet.add(subnet6);
			}

			final LnxNetLink link = new LnxNetLink(//
					new FirewallDelegateFilter(nodeRuntime, //
							Collections.unmodifiableSet(hostIpSet), //
							Collections.unmodifiableSet(subnetSet)), //
					hostIfaceName, contextIfaceName, //
					subnet4, hostAddr4, contextAddr4, //
					subnet6, hostAddr6, contextAddr6);
			this._activeLinks.put(linkNum, link);

			try {

				wsExecAndJoinCmd(//
						Arrays.asList("sudo", "ip", "link", "add", hostIfaceName, "type", "veth", "peer", "name",
								contextIfaceName)//
						, true);
				wsExecAndJoinCmd(//
						Arrays.asList("sudo", "ip", "link", "set", contextIfaceName, "netns",
								Long.toString(this.processId))//
						, true);
				if (subnet4 != null) {
					wsExecAndJoinCmd(//
							Arrays.asList("sudo", "ip", "addr", "add", link.getHostAddressV4()
									.toCidrNotation() + "/30", "dev", hostIfaceName)//
							, true);
				}
				if (subnet6 != null) {
					wsExecAndJoinCmd(//
							Arrays.asList("sudo", "ip", "addr", "add", link.getHostAddressV6()
									.toCidrNotation() + "/126", "dev", hostIfaceName)//
							, true);
				}
				wsExecAndJoinCmd(//
						Arrays.asList("sudo", "ip", "link", "set", hostIfaceName, "up")//
						, true);
				List<String> cmd;
				if (subnet4 != null) {
					cmd = nsenterNetCmd(this.processId);
					cmd.addAll(Arrays.asList("ip", "addr", "add", link.getContextAddressV4()
							.toCidrNotation() + "/30", "dev", contextIfaceName));
					wsExecAndJoinCmd(cmd, true);
				}
				if (subnet6 != null) {
					cmd = nsenterNetCmd(this.processId);
					cmd.addAll(Arrays.asList("ip", "addr", "add", link.getContextAddressV6()
							.toCidrNotation() + "/126", "dev", contextIfaceName));
					wsExecAndJoinCmd(cmd, true);
				}
				cmd = nsenterNetCmd(this.processId);
				cmd.addAll(Arrays.asList("ip", "link", "set", contextIfaceName, "up"));
				wsExecAndJoinCmd(cmd, true);

				if (nodeRuntime.isolationHandler() instanceof LnxNodeIsolationHandler handler) {
					handler.networkHostInterfaceAdd(Set.of(hostIfaceName), nodeLog);
				}
			} catch (InterruptedException | IOException e) {
				final PrintStream pipeLog = this.listener.getLogger();
				pipeLog.println(String.format(TEMPL_LINE, this.processId,
						"Failed to connect host-only veth-link to isolated environment!"));
				e.printStackTrace(pipeLog);

				disconnectNetLink(link);
				return null;
			}
			return link;
		}
	}

	@Override
	public void disconnectNetLink(final NetLink anyLink) throws IOException, InterruptedException {
		if (!(anyLink instanceof LnxNetLink))
			return;
		final LnxNetLink link = (LnxNetLink) anyLink;
		final NodeIsolationRuntime nodeRuntime = getNodeRuntime();

		final TaskListener nodeLog = logListener();

		synchronized (this.linkLock) {
			if (!this._activeLinks.containsValue(link))
				return;

			try {
				wsExecAndJoinCmd(Arrays.asList("sudo", "ip", "link", "del", link.getHostIfaceName()), true);

				if (nodeRuntime != null) {
					nodeRuntime.freeIpv4Subnet(link.getSubnetV4(), nodeLog);
					nodeRuntime.freeIpv6Subnet(link.getSubnetV6(), nodeLog);
				}
			} finally {
				if (nodeRuntime.isolationHandler() instanceof LnxNodeIsolationHandler handler) {
					handler.networkHostInterfaceRemove(Set.of(link.getHostIfaceName()), nodeLog);
				}
			}
		}
	}

	protected Proc wsExecAndJoinCmd(final List<String> cmd, final boolean readStderr)
			throws IOException, InterruptedException {
		return wsExecAndJoinCmd(cmd, readStderr, 0);
	}

	protected Proc wsExecAndJoinCmd(final List<String> cmd, final boolean readStderr, final int expectedExitCode)
			throws IOException, InterruptedException {
		final ProcStarter procStarter = getWorkspace().createLauncher(logListener())
				.launch()
				.cmds(cmd)
				.readStdout();
		if (readStderr)
			procStarter.readStderr();

		final Proc proc = procStarter.start();
		if (readStderr)
			joinAndLogProcessException(proc, expectedExitCode);
		else
			proc.join();
		return proc;
	}

	protected void joinAndLogProcessException(final Proc process, final int expectedExitCode)
			throws IOException, InterruptedException {
		final PrintStream nodeLog = logListener().getLogger();
		final int exitCode = process.join();
		if (exitCode == expectedExitCode)
			return;
		nodeLog.println("exitCode=" + exitCode);
		try {
			final InputStream errStream = process.getStderr();
			if (errStream == null)
				return;
			errStream.transferTo(nodeLog);
		} catch (IOException e) {
			e.printStackTrace(nodeLog);
		}
	}

	protected static String outputToString(final Proc proc) {
		try {
			return new String(proc.getStdout()
					.readAllBytes()).trim();
		} catch (IOException e) {
			throw new CompletionException(e);
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

		private final LinuxNodeNamespaceContext ctx;

		protected DecoratorFactory(final LinuxNodeNamespaceContext lnxContext) {
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
						envFileName = DecoratorFactory.this.ctx.envCachePath()
								.act(new WriteEnvFile(env));
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

	public static class IOTriplet2 extends Launcher.IOTriplet {
		@CheckForNull
		public InputStream stdout, stderr;
		@CheckForNull
		public OutputStream stdin;
		private static final long serialVersionUID = 1L;
	}

	public static interface RemoteProcess2 extends Launcher.RemoteProcess {

		public long pid();

		@NonNull
		IOTriplet2 getIOtriplet();
	}

	public static class ExecRemoteProcess implements ControllerToAgentFileCallable<RemoteProcess2> {

		private static final long serialVersionUID = 1L;

		protected final List<String> cmd;

		public ExecRemoteProcess(final List<String> cmd) {
			this.cmd = cmd;
		}

		@Override
		public RemoteProcess2 invoke(final File wsDir, final VirtualChannel channel)
				throws IOException, InterruptedException {
			final ProcessBuilder pb = new ProcessBuilder(cmd);

			// final Map<String, String> env = pb.environment();
			// env.clear();
			// env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin");
			// env.put("LANG", "C");
			// env.put("LC_ALL", "C");

			pb.directory(wsDir);
			final Process process = pb.start();

			return channel.export(RemoteProcess2.class, new RemoteProcess2() {

				@Override
				public long pid() {
					return process.pid();
				}

				@Override
				public void kill() throws IOException, InterruptedException {
					process.destroy();
				}

				@Override
				public int join() throws InterruptedException, IOException {
					try {
						return process.waitFor();
					} catch (InterruptedException e) {
						kill();
						throw e;
					}
				}

				@Override
				public boolean isAlive() throws IOException, InterruptedException {
					return process.isAlive();
				}

				@Override
				public IOTriplet2 getIOtriplet() {
					IOTriplet2 r = new IOTriplet2();
					r.stdout = new RemoteInputStream(process.getInputStream(), RemoteInputStream.Flag.NOT_GREEDY);
					r.stderr = new RemoteInputStream(process.getErrorStream(), RemoteInputStream.Flag.NOT_GREEDY);
					r.stdin = new RemoteOutputStream(process.getOutputStream());
					return r;
				}
			});
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
