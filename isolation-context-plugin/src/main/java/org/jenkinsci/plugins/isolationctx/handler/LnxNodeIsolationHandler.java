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
package org.jenkinsci.plugins.isolationctx.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.isolationctx.Scripts;
import org.jenkinsci.plugins.isolationctx.ctx.LinuxNodeNamespaceContext;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import jenkins.util.io.OnMaster;

public class LnxNodeIsolationHandler implements NodeIsolationHandler, OnMaster {

	private static final Logger LOGGER = Logger.getLogger(LinuxNodeNamespaceContext.class.getName());

	protected final AtomicBoolean active = new AtomicBoolean(false);

	@NonNull
	protected final Node node;
	@NonNull
	protected final Computer computer;
	@NonNull
	protected final FilePath logPath;
	@NonNull
	protected final FilePath rootPath;
	@NonNull
	protected final FilePath dataRootPath;
	@NonNull
	protected final FilePath firewallToolPath;
	@NonNull
	protected final FilePath mountToolPath;
	@CheckForNull
	protected TaskListener logListener;

	public LnxNodeIsolationHandler(@NonNull final Node node) {
		Objects.requireNonNull(this.node = node);
		Objects.requireNonNull(this.computer = node.toComputer());

		this.logPath = node.getRootPath()
				.child("logs/NodeIsolation.log");
		this.rootPath = this.node.getRootPath()
				.child("isolation-context");
		this.dataRootPath = this.rootPath.child("data");
		this.firewallToolPath = this.rootPath.child("firewall-tool.py");
		this.mountToolPath = this.rootPath.child("mount-tool.py");
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	@Override
	public synchronized TaskListener logTaskListener() {
		if (this.logListener != null)
			return this.logListener;
		try {
			return this.logListener = new StreamTaskListener(this.logPath.write(), StandardCharsets.UTF_8);
		} catch (IOException | InterruptedException e) {
			final TaskListener listener = new LogTaskListener(LOGGER, Level.INFO);
			listener.error("Failed to create Remote-Logger, redirection activated!");
			e.printStackTrace(listener.getLogger());
			return this.logListener = listener;
		}
	}

	protected void execScriptTask(final Collection<String> tasks, final TaskListener listener)
			throws IOException, InterruptedException {
		// deploy file from resources -> path
		final FilePath networkPath = this.rootPath.child("network-tool.py");
		// jar:/scripts/network/elinux-python3.py
		Scripts.NETWORK_ENTERPRISE_LINUX_PYTHON3.deployTo(networkPath);
		// jar:/scripts/firewall/nftables-python3.py
		Scripts.FIREWALL_NFTABLES_PYTHON3.deployTo(this.firewallToolPath);
		// jar:/scripts/data/linux-mount-python3.py
		Scripts.DATA_LINUX_MOUNT_PYTHON3.deployTo(this.mountToolPath);

		final Launcher launcher = this.node.createLauncher(listener);
		// network setup
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", networkPath.absolutize()
						.getRemote()).add(tasks))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
		// firewall setup
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallToolPath.absolutize()
						.getRemote()).add(tasks))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
		// mount isolation setup
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.mountToolPath.absolutize()
						.getRemote()).add(tasks))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
	}

	@Override
	public void setup(final TaskListener listener) throws IOException, InterruptedException {
		try {
			execScriptTask(Arrays.asList("setup"), listener);
			this.active.set(true);
		} catch (Throwable e) {
			this.active.set(false);
			throw e;
		}
	}

	@Override
	public void shutdown(final TaskListener listener) throws IOException, InterruptedException {
		try {
			execScriptTask(Arrays.asList("shutdown"), listener);
			this.active.set(false);
		} catch (Throwable e) {
			throw e;
		}
	}

	protected void networkHostInterfaceExec(final Set<String> data, final String operation, final TaskListener listener)
			throws IOException, InterruptedException {
		this.dataRootPath.mkdirs();
		final FilePath tmpFilePath = this.dataRootPath.createTempFile("hostif-", ".csv");
		try {
			try (OutputStream stream = tmpFilePath.write()) {
				for (String hostif : data) {
					stream.write(String.format("%s\n", hostif)
							.getBytes(StandardCharsets.UTF_8));
				}
				stream.flush();
			}
			final Launcher launcher = this.node.createLauncher(listener);
			launcher.launch()
					.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3",
							this.firewallToolPath.absolutize()
									.getRemote()).add("set", "hostif", operation, tmpFilePath.absolutize()
											.getRemote()))
					.start()
					.joinWithTimeout(30, TimeUnit.SECONDS, listener);
		} finally {
			tmpFilePath.delete();
		}
	}

	public void networkHostInterfaceAdd(final Set<String> data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		networkHostInterfaceExec(data, "add", listener);
	}

	public void networkHostInterfaceRetain(final Set<String> data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		networkHostInterfaceExec(data, "set", listener);
	}

	public void networkHostInterfaceRemove(final Set<String> data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		networkHostInterfaceExec(data, "del", listener);
	}

	public void networkHostInterfaceRemoveAll(final TaskListener listener) throws IOException, InterruptedException {
		final Launcher launcher = this.node.createLauncher(listener);
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallToolPath.absolutize()
						.getRemote()).add("set", "hostif", "clear"))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
	}

	protected void firewallInputExec(final TrafficMap data, final String operation, final TaskListener listener)
			throws IOException, InterruptedException {
		this.dataRootPath.mkdirs();
		final FilePath tmpFilePath = this.dataRootPath.createTempFile("traffic-", ".csv");
		try {
			try (OutputStream stream = tmpFilePath.write()) {
				data.forEach((host, external, protoMap) -> {
					if (host == null || external == null || protoMap == null || protoMap.isEmpty())
						return;
					for (Map.Entry<TrafficMap.Protocol, Set<Integer>> entry : protoMap.entrySet()) {
						final TrafficMap.Protocol protocol = entry.getKey();
						final Set<Integer> ports = entry.getValue();
						if (protocol == null || ports == null || ports.isEmpty())
							continue;
						for (Integer port : ports) {
							stream.write(String
									.format("%d|%s|%s|%s|%d\n", host.getIpFamily(), host.toCidrNotation(),
											external.toCidrNotation(), protocol.toString(), port)
									.getBytes(StandardCharsets.UTF_8));
						}
					}
					stream.flush();
				});
				stream.flush();
			}
			this.node.createLauncher(listener)
					.launch()
					.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3",
							this.firewallToolPath.absolutize()
									.getRemote()).add("set", "traffic-port", operation, tmpFilePath.absolutize()
											.getRemote()))
					.start()
					.joinWithTimeout(30, TimeUnit.SECONDS, listener);
		} finally {
			tmpFilePath.delete();
		}
	}

	@Override
	public void firewallInputOpen(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		firewallInputExec(data, "add", logTaskListener());
	}

	@Override
	public void firewallInputRetainOpen(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		firewallInputExec(data, "set", logTaskListener());
	}

	@Override
	public void firewallInputClose(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		firewallInputExec(data, "del", logTaskListener());
	}

	@Override
	public void firewallInputCloseAll(final TaskListener listener) throws IOException, InterruptedException {
		final TaskListener nodeLog = logTaskListener();
		final Launcher launcher = this.node.createLauncher(nodeLog);
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallToolPath.absolutize()
						.getRemote()).add("set", "traffic-port", "clear"))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, nodeLog);
	}

	protected void mountToolExec(final List<String> operations, final TaskListener listener)
			throws IOException, InterruptedException {
		final Launcher launcher = this.node.createLauncher(listener);
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.mountToolPath.absolutize()
						.getRemote()).add(operations))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
	}

	public OverlayMountData overlayCreate(final FilePath overlayBasePath) throws IOException, InterruptedException {
		final TaskListener nodeLog = logTaskListener();
		mountToolExec(Arrays.asList("mount", "overlay", "create", overlayBasePath.absolutize()
				.getRemote()), nodeLog);
		final OverlayMountData overlay = new OverlayMountData(this, overlayBasePath);
		return overlay;
	}

	public void overlayRemove(final FilePath overlayBasePath) throws IOException, InterruptedException {
		final TaskListener nodeLog = logTaskListener();
		mountToolExec(Arrays.asList("mount", "overlay", "cleanup", overlayBasePath.absolutize()
				.getRemote()), nodeLog);

	}

	public static class OverlayMountData implements Serializable {

		private static final long serialVersionUID = 1L;

		protected final Map<String, String> exportedMounts = new LinkedHashMap<>();
		protected final Set<String> bindMounts = new LinkedHashSet<>();

		// /var/jenkins/iso/<job>/
		protected final String basePath;
		// /var/jenkins/iso/<job>/root
		protected final String rootPath;

		protected transient LnxNodeIsolationHandler handler;

		protected OverlayMountData(final LnxNodeIsolationHandler handler, final FilePath overlayBasePath) {
			this.handler = handler;
			this.basePath = overlayBasePath.getRemote();
			this.rootPath = overlayBasePath.child("root")
					.getRemote();
		}

		public void initHandler(final LnxNodeIsolationHandler handler) {
			this.handler = handler;
		}

		public FilePath basePath() {
			return new FilePath(this.handler.node.getChannel(), this.basePath);
		}

		public FilePath rootPath() {
			return new FilePath(this.handler.node.getChannel(), this.rootPath);
		}

		public synchronized boolean export(String name, final FilePath exportPath)
				throws IOException, InterruptedException {
			Objects.requireNonNull(name = Util.fixEmptyAndTrim(name));
			final String exportRemote = exportPath.absolutize()
					.getRemote();

			if (this.exportedMounts.containsKey(name))
				return false;

			this.handler.mountToolExec(Arrays.asList("mount", "export", basePath().child("export-" + name)
					.absolutize()
					.getRemote(),
					rootPath().child(
							/* trim starting / to make it relative */
							exportRemote.substring(1))
							.absolutize()
							.getRemote()),
					this.handler.logTaskListener());
			this.exportedMounts.put(name, exportRemote);
			return true;
		}

		public synchronized boolean bind(final FilePath bindPath) throws IOException, InterruptedException {
			final String bindRemote = bindPath.absolutize()
					.getRemote();
			final Path compPath = Path.of(bindPath.absolutize()
					.getRemote());
			if (!this.exportedMounts.values()
					.stream()
					.anyMatch(compPath::startsWith))
				throw new IllegalArgumentException("No Parent path was exported of: " + bindRemote);

			if (this.bindMounts.contains(bindRemote))
				return false;

			this.handler.mountToolExec(Arrays.asList("mount", "bind", bindRemote,
					rootPath().child(bindRemote.substring(1))
							.absolutize()
							.getRemote()),
					this.handler.logTaskListener());
			this.bindMounts.add(bindRemote);
			return true;
		}

		public boolean unbind(final FilePath bindPath) throws IOException, InterruptedException {
			final String bindRemote = bindPath.absolutize()
					.getRemote();
			this.handler.mountToolExec(Arrays.asList("mount", "unbind", rootPath().child(bindRemote.substring(1))
					.absolutize()
					.getRemote()), this.handler.logTaskListener());
			this.bindMounts.remove(bindRemote);
			return true;
		}
	}
}
