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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationProperty;
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationRuntime;
import org.jenkinsci.plugins.isolationctx.config.NodeIsolationRuntimeAction;
import org.jenkinsci.plugins.isolationctx.ctx.net.LnxNetLink;
import org.jenkinsci.plugins.isolationctx.dto.LinuxMountEntry;
import org.jenkinsci.plugins.isolationctx.dto.LinuxIdentity;
import org.jenkinsci.plugins.isolationctx.handler.LnxNodeIsolationHandler;
import org.jenkinsci.plugins.isolationctx.handler.LnxNodeIsolationHandler.OverlayMountData;
import org.jenkinsci.plugins.isolationctx.util.WorkspaceLauncherUtil;
import org.jenkinsci.plugins.isolationctx.handler.OciRuntimeHandler;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.google.common.io.Files;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
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
import jenkins.model.Jenkins;
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

	protected final DecoratorFactory factory;
	protected final String containerId;

	protected long processId = -1;
	protected OverlayMountData overlayData = null;

	protected transient final Object linkLock = new Object();

	protected transient final WorkspaceLauncherUtil launchUtil = //
			new WorkspaceLauncherUtil(this::getWorkspace, this::logListener);;
	protected transient Run<?, ?> run;
	protected transient Node node;
	protected transient WorkspaceAction ws;
	protected transient TaskListener listener;

	protected transient final SortedMap<Integer, LnxNetLink> _activeLinks = new TreeMap<>();
	protected transient CompletableFuture<LinuxIdentity> _lnxIdentityFuture = null;
	protected transient TaskListener _logListener = null;

	public LinuxNodeNamespaceContext(final Run<?, ?> run, final Node node, final WorkspaceAction ws,
			final TaskListener listener) {
		this.factory = new DecoratorFactory(this);
		this.run = run;
		this.node = node;
		this.ws = ws;
		this.listener = listener;

		this.containerId = "jenkins_" + this.run.getParent()
				.getFullName()
				.replace('/', '-');
	}

	@Override
	public void onLoad(final FlowNode parent) {
		Objects.requireNonNull(parent, "LinuxNodeNamespaceContext can not be loaded with FlowNode: null");

		this.ws = parent.getAction(WorkspaceAction.class);
		this.node = Jenkins.get()
				.getNode(this.ws.getNode());

		final String flowNodeId = parent.getId();
		try {
			StepExecution.acceptAll(exec -> {
				try {
					final StepContext stepContext = exec.getContext();
					final String id = stepContext.get(FlowNode.class)
							.getId();

					if (flowNodeId.equals(id)) {
						LinuxNodeNamespaceContext.this.run = stepContext.get(Run.class);
						LinuxNodeNamespaceContext.this.listener = stepContext.get(TaskListener.class);
					}
				} catch (Exception ignore) {
				}
			})
					.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		// getNodeRuntime2() requires this.node to be set
		final NodeIsolationRuntime runtime = getNodeRuntime2();
		if (runtime != null && runtime.isolationHandler() instanceof LnxNodeIsolationHandler handler) {
			// reinit overlayData
			if (this.overlayData != null)
				this.overlayData.initHandler(handler);
		}
	}

	@Override
	public LauncherDecorator launcherDecorator() {
		return this.factory;
	}

	protected FilePath envCachePath() {
		return this.ws.getWorkspace()
				.withSuffix("@env_cache");
	}

	public synchronized CompletableFuture<LinuxIdentity> lnxIdentityFuture() {
		if (this._lnxIdentityFuture != null)
			return this._lnxIdentityFuture;
		return this._lnxIdentityFuture = //
				CompletableFuture.supplyAsync(() -> LinuxIdentity.identify(this.launchUtil))
						.thenApply(id -> id.removeGroupIds(//
								LinuxIdentity.groupIdByName(this.launchUtil,
										NodeIsolationProperty.getAdminGroup(this.node))));
	}

	public LinuxIdentity lnxIdentity() throws IOException, InterruptedException {
		try {
			return lnxIdentityFuture().get();
		} catch (ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof IOException ex)
				throw ex;
			if (cause instanceof InterruptedException ex)
				throw ex;
			if (cause instanceof RuntimeException ex)
				throw ex;
			throw new RuntimeException(cause);
		}
	}

	protected NodeIsolationRuntime getNodeRuntime() throws IOException, InterruptedException {
		return NodeIsolationRuntimeAction.get(this.node);
	}

	protected NodeIsolationRuntime getNodeRuntime2() {
		try {
			return getNodeRuntime();
		} catch (IOException | InterruptedException ignored) {
			ignored.printStackTrace();
		}
		return null;
	}

	public synchronized TaskListener logListener() {
		if (this._logListener != null)
			return _logListener;
		try {
			return _logListener = getNodeRuntime2().isolationHandler()
					.logTaskListener();
		} catch (Throwable e) {
			this._logListener = new LogTaskListener(LOGGER, Level.INFO);
			this._logListener.error("Failed to create Remote-Logger, redirection activated!");
			e.printStackTrace(this._logListener.getLogger());
			return this._logListener;
		}
	}

	protected OciRuntimeHandler ociHandler() throws IOException, InterruptedException {
		final NodeIsolationRuntime nodeRuntime = getNodeRuntime();
		if (nodeRuntime != null && nodeRuntime.isolationHandler() instanceof OciRuntimeHandler handler)
			return handler;
		return null;
	}

	public List<String> nsenterCmd(final boolean isolateNetwork, FilePath workspace) throws Throwable {
		if (workspace == null)
			workspace = this.ws.getWorkspace();
		final List<String> cmd = new ArrayList<>();

		final LinuxIdentity lnxIdentity = lnxIdentity();

		// sudo nsenter --target=75008 --mount --uts --ipc --net --pid --
		cmd.add("sudo");
		cmd.add("nsenter");
		cmd.add("--target=" + LinuxNodeNamespaceContext.this.processId);
		cmd.add("--mount");
		cmd.add("--uts");
		cmd.add("--ipc");
		if (isolateNetwork)
			cmd.add("--net");
		cmd.add("--pid");
		// --wdns is not yet available on EL9
		// cmd.add("--wdns=" + workspace.absolutize().getRemote());
		cmd.add("--");
		// setpriv --reuid=<uid> --regid=<gid> --groups=<groups> --inh-caps=-all
		// --reset-env
		// --
		cmd.add("setpriv");
		cmd.add("--reuid=" + lnxIdentity.getUserId());
		cmd.add("--regid=" + lnxIdentity.getGroupId());
		cmd.add("--groups=" + lnxIdentity.joinGroupIds(","));
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

		try {
			final OciRuntimeHandler ociHandler = ociHandler();
			if (ociHandler == null) {
				pipeLog.println("[Linux-Namespace] Failed to start isolated environment, Oci-Runtime unavailable!");
				return;
			}

			final FilePath workspacePath = this.ws.getWorkspace();
			final String workspacePathRemote = workspacePath.absolutize()
					.getRemote();
			workspacePath.mkdirs();
			final FilePath envCachePath = envCachePath();
			final String envCachePathRemote = envCachePath.absolutize()
					.getRemote();
			envCachePath.mkdirs();
			final FilePath tmpPath = workspacePath.withSuffix("@tmp");
			final String tmpPathRemote = tmpPath.absolutize()
					.getRemote();
			tmpPath.mkdirs();

			FilePath ociBundlePath = workspacePath.withSuffix("@ociBundle");

			final OciConfigFileCallable ociConfigBuilder = //
					new OciConfigFileCallable(lnxIdentity(), "jenkins-agent") //
							.addProcMount()
							.addTmpfsMount("/tmp", "nosuid", "nodev", "mode=1777", "size=1g")
							.addTmpfsMount("/dev", "nosuid", "strictatime", "mode=755", "size=64m")
							.addBindMount("/dev/null", "/dev/null", "rbind", "rw")
							.addBindMount("/dev/zero", "/dev/zero", "rbind", "rw")
							.addBindMount("/dev/full", "/dev/full", "rbind", "rw")
							.addBindMount("/dev/random", "/dev/random", "rbind", "rw")
							.addBindMount("/dev/urandom", "/dev/urandom", "rbind", "rw")
							.addTmpfsMount("/dev/shm", "nosuid", "nodev", "mode=1777", "size=64m")
							.addTmpfsMount("/boot", "mode=700", "size=64k")
							.addTmpfsMount(Jenkins.get()
									.getRootDir()
									.getAbsolutePath(), "mode=755", "size=64m")
							.addBindMount(workspacePathRemote, workspacePathRemote, "bind", "rw", "rshared")
							.addBindMount(tmpPathRemote, tmpPathRemote, "bind", "rw", "rshared")
							.addBindMount(envCachePathRemote, envCachePathRemote, "bind", "rw", "rshared");

			if (ociHandler instanceof LnxNodeIsolationHandler handler) {
				final OverlayMountData mountData = handler.overlayCreate(workspacePath.withSuffix("@overlay"));
				ociConfigBuilder.setRootFs("rootfs");
				ociBundlePath = mountData.basePath();
				mountData.computeWorkspacePath(workspacePath);
				this.overlayData = mountData;
			}

			ociBundlePath.mkdirs();
			final FilePath ociConfigPath = ociBundlePath.child("config.json");
			ociConfigPath.act(ociConfigBuilder);

			this.processId = ociHandler.ociRuntimeStart(ociBundlePath, containerId, this.listener);
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
			final OciRuntimeHandler ociHandler = ociHandler();
			if (ociHandler == null) {
				pipeLog.println("[Linux-Namespace] Failed to stop isolated environment, Oci-Runtime unavailable!");
				return;
			}

			ociHandler.ociRuntimeKill(this.processId, this.containerId, this.listener);
			envCachePath().deleteRecursive();

			pipeLog.println(String.format(TEMPL_LINE, this.processId, "Stopped isolated environment"));
			this.processId = -1;
		} catch (InterruptedException | IOException e) {
			pipeLog.println(String.format(TEMPL_LINE, this.processId, "Failed to stop isolated environment!"));
			e.printStackTrace(nodeLog);
		} finally {
			// Cleanup active LnxLinks
			final NodeIsolationRuntime nodeRuntime = getNodeRuntime2();
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
		if (this.overlayData == null) {
			return this.ws.getWorkspace();
		}
		return this.overlayData.workspacePath();
	}

	@Override
	public <T> T actInContext(final FileCallable<T> callable) throws IOException, InterruptedException {
		return getWorkspace().act(callable);
	}

	@Override
	public <T> Future<T> actInContextAsync(final FileCallable<T> callable) throws IOException, InterruptedException {
		return getWorkspace().actAsync(callable);
	}

	@Override
	public boolean mountWorkspacePath(final FilePath workspacePath) throws IOException, InterruptedException {
		final OciRuntimeHandler ociHandler = ociHandler();
		if (ociHandler == null || !workspacePath.exists() || this.processId == -1)
			return false;
		final String workspacePathRemote = workspacePath.absolutize()
				.getRemote();
		final LinuxMountEntry entry = new LinuxMountEntry("bind", workspacePathRemote, workspacePathRemote);
		return ociHandler.ociRuntimeMountsAdd(this.processId, this.containerId, Set.of(entry), this.listener);
	}

	@Override
	public boolean unmountWorkspacePath(final FilePath workspacePath) throws IOException, InterruptedException {
		final OciRuntimeHandler ociHandler = ociHandler();
		if (ociHandler == null || !workspacePath.exists() || this.processId == -1)
			return false;
		final String workspacePathRemote = workspacePath.absolutize()
				.getRemote();
		final LinuxMountEntry entry = new LinuxMountEntry("bind", workspacePathRemote, workspacePathRemote);
		return ociHandler.ociRuntimeMountsRemove(this.processId, this.containerId, Set.of(entry), this.listener);
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
		final NodeIsolationRuntime nodeRuntime = getNodeRuntime2();
		if (nodeRuntime == null || this.processId == -1)
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
		final NodeIsolationRuntime nodeRuntime = getNodeRuntime2();

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
		return this.launchUtil.wsExecAndJoinCmd(cmd, readStderr);
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
			return new DecoratedIsolationLauncher(launcher) {
				@Override
				public Proc launch(final ProcStarter starter) throws IOException {
					if (DecoratorFactory.this.ctx.processId == -1)
						throw new AbortException("[Linux-Namespace] Namespace unavailable!");

					// Wrap command with nsenter
					final List<String> cmd;
					final String envFileName;
					try {
						cmd = DecoratorFactory.this.ctx.nsenterCmd(isNetworkIsolated(), starter.pwd());
						final FilePath envCachePath = DecoratorFactory.this.ctx.envCachePath();
						envFileName = envCachePath.act(
								new WriteTempEnvFileCallable(inherit(envCachePath.getChannel(), starter.envs()), true));
					} catch (Throwable e) {
						if (e instanceof RuntimeException runE)
							throw runE;
						throw new RuntimeException(e);
					}

					// as nsenter from linux-utils on EL9 can not change the working dir yet,
					// do it now!
					String cdCmd = "";
					{
						FilePath workspace = starter.pwd();
						if (workspace == null)
							workspace = DecoratorFactory.this.ctx.ws.getWorkspace();
						try {
							cdCmd = "cd \"" + workspace.absolutize()
									.getRemote() + "\"; ";
						} catch (IOException | InterruptedException ignored) {
							ignored.printStackTrace();
						}
					}
					cmd.addAll(Arrays.asList("sh", "-c", "(" + cdCmd + ". \"" + envFileName + "\"; exec \"$@\")"));

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

		/**
		 * Expands the list of environment variables by inheriting current env
		 * variables.
		 *
		 * @throws IOException
		 * @throws InterruptedException
		 */
		private static EnvVars inherit(@NonNull final VirtualChannel channel, @NonNull final String[] env)
				throws IOException, InterruptedException {
			// convert String[] to Map first
			EnvVars m = new EnvVars();
			if (env != null) {
				for (String e : env) {
					int index = e.indexOf('=');
					m.put(e.substring(0, index), e.substring(index + 1));
				}
			}
			m = EnvVars.getRemote(channel)
					.overrideExpandingAll(m);

			// apply filters
			for (Iterator<Map.Entry<String, String>> i = m.entrySet()
					.iterator(); i.hasNext();) {
				final Map.Entry<String, String> entry = i.next();
				final String key;
				// Filter out bash functions
				if (entry == null || (key = entry.getKey()) == null || key.endsWith("%%"))
					i.remove();
			}

			return m;
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

	public static class OciConfigFileCallable extends MasterToSlaveFileCallable<Void> {

		private static final long serialVersionUID = 1L;

		public static final String OCI_VERSION = "1.0.2";

		protected final List<LinuxMountEntry> mounts = new LinkedList<>();

		protected final LinuxIdentity identity;
		protected final String hostname;

		protected String rootfs = "/";

		public OciConfigFileCallable(final LinuxIdentity identity, final String hostname) {
			this.identity = identity;
			this.hostname = hostname;
		}

		public OciConfigFileCallable setRootFs(final String rootfs) {
			this.rootfs = rootfs;
			return this;
		}

		public OciConfigFileCallable addProcMount() {
			this.mounts.add(new LinuxMountEntry("proc", "proc", "/proc"));
			return this;
		}

		public OciConfigFileCallable addTmpfsMount(final String destination, final String... options) {
			this.mounts.add(new LinuxMountEntry("tmpfs", "tmpfs", destination).options(options));
			return this;
		}

		public OciConfigFileCallable addBindMount(final String source, final String destination,
				final String... options) {
			this.mounts.add(new LinuxMountEntry("bind", source, destination).options(options));
			return this;
		}

		@Override
		public Void invoke(final File configFile, final VirtualChannel channel)
				throws IOException, InterruptedException {
			Files.write(toJson().getBytes(), configFile);
			return null;
		}

		public String toJson() {
			return "{\"ociVersion\":\"" + OCI_VERSION + "\"," //
					+ "\"process\":{\"terminal\": false," //
					+ "\"user\":{\"uid\":" + this.identity.getUserId() + ",\"gid\":" + this.identity.getGroupId() + "}," //
					+ "\"args\":[\"/usr/bin/tail\",\"-f\",\"/dev/null\"],\"env\":[],\"cwd\":\"/\"}," //
					+ "\"root\":{\"path\":\"" + this.rootfs + "\",\"readonly\":false}," //
					+ "\"mounts\":[" //
					+ this.mounts.stream()
							.map(LinuxMountEntry::toOciJson)
							.collect(Collectors.joining(",")) //
					+ "],\"hostname\":\"" + this.hostname //
					+ "\",\"linux\":{\"namespaces\":[{\"type\":\"uts\"},{\"type\":\"ipc\"}," //
					+ "{\"type\":\"network\"},{\"type\":\"pid\"},{\"type\":\"mount\"}]}}\n";
		}
	}
}
