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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jenkinsci.plugins.isolationctx.Scripts;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import jenkins.util.io.OnMaster;

public class LnxNodeIsolationHandler implements NodeIsolationHandler, OnMaster {

	@NonNull
	protected final Node node;
	@NonNull
	protected final Computer computer;
	@NonNull
	protected final FilePath rootPath;
	@NonNull
	protected final FilePath dataRootPath;
	@NonNull
	protected final FilePath firewallPath;

	protected AtomicBoolean active = new AtomicBoolean(false);

	public LnxNodeIsolationHandler(@NonNull final Node node) {
		Objects.requireNonNull(this.node = node);
		Objects.requireNonNull(this.computer = node.toComputer());

		this.rootPath = this.node.getRootPath()
				.child("isolation-context");
		this.dataRootPath = this.rootPath.child("data");
		this.firewallPath = this.rootPath.child("firewall-tool.py");
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	protected void execScriptTask(final Collection<String> tasks, final TaskListener listener)
			throws IOException, InterruptedException {
		// deploy file from resources -> path
		final FilePath networkPath = this.rootPath.child("network-tool.py");
		// jar:/scripts/network/elinux-python3.py
		Scripts.NETWORK_ENTERPRISE_LINUX_PYTHON3.deployTo(networkPath);
		// jar:/scripts/firewall/nftables-python3.py
		Scripts.FIREWALL_NFTABLES_PYTHON3.deployTo(this.firewallPath);

		final Launcher launcher = this.node.createLauncher(listener);
		// network setup
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", networkPath.absolutize()
						.getRemote()).add(tasks))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
		// firewall setup
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallPath.absolutize()
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
					.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallPath.absolutize()
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
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallPath.absolutize()
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
			final Launcher launcher = this.node.createLauncher(listener);
			launcher.launch()
					.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallPath.absolutize()
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
		firewallInputExec(data, "add", listener);
	}

	@Override
	public void firewallInputRetainOpen(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		firewallInputExec(data, "set", listener);
	}

	@Override
	public void firewallInputClose(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		if (data == null || data.isEmpty())
			return;
		firewallInputExec(data, "del", listener);
	}

	@Override
	public void firewallInputCloseAll(final TaskListener listener) throws IOException, InterruptedException {
		final Launcher launcher = this.node.createLauncher(listener);
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", this.firewallPath.absolutize()
						.getRemote()).add("set", "traffic-port", "clear"))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
	}

}
