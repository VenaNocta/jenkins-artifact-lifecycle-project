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
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jenkinsci.plugins.isolationctx.Scripts;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import jenkins.util.io.OnMaster;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class LnxNodeIsolationHandler implements NodeIsolationHandler, OnMaster {

	@NonNull
	protected final Node node;
	@NonNull
	protected final Computer computer;
	@NonNull
	protected final FilePath rootPath;

	protected AtomicBoolean active = new AtomicBoolean(false);

	public LnxNodeIsolationHandler(@NonNull final Node node) {
		Objects.requireNonNull(this.node = node);
		Objects.requireNonNull(this.computer = node.toComputer());
		Objects.requireNonNull(this.rootPath = this.node.getRootPath()
				.child("isolation-context"));
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
		final FilePath firewallPath = this.rootPath.child("firewall-tool.py");
		// jar:/scripts/firewall/nftables-python3.py
		Scripts.FIREWALL_NFTABLES_PYTHON3.deployTo(firewallPath);

		final Launcher launcher = this.node.createLauncher(listener);
		// network setup
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", networkPath.absolutize()
						.getRemote()).add(tasks))
				.start()
				.joinWithTimeout(30, TimeUnit.SECONDS, listener);
		// firewall setup
		launcher.launch()
				.cmds(new ArgumentListBuilder("/usr/bin/sudo", "/usr/bin/python3", firewallPath.absolutize()
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

	@Override
	public void firewallInputOpen(IpAddress<?> host, IpAddress<?> external, Set<Integer> ports, TaskListener listener)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub

	}

	@Override
	public void firewallInputOpen(Map<IpAddress<?>, Map<IpAddress<?>, Set<Integer>>> data, TaskListener listener)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub

	}

	@Override
	public void firewallInputRetainOpen(Map<IpAddress<?>, Map<IpAddress<?>, Set<Integer>>> data, TaskListener listener)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub

	}

	@Override
	public void firewallInputCloseAll(TaskListener listener) throws IOException, InterruptedException {
		// TODO Auto-generated method stub

	}

	@Override
	public void firewallInputClose(IpAddress<?> host, IpAddress<?> external, Set<Integer> ports, TaskListener listener)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub

	}

	@Override
	public void firewallInputClose(Map<IpAddress<?>, Map<IpAddress<?>, Set<Integer>>> data, TaskListener listener)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub

	}
}
