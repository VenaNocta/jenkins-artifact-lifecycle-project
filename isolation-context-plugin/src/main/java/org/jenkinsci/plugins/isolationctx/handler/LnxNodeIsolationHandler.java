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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import jenkins.util.io.OnMaster;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class LnxNodeIsolationHandler implements NodeIsolationHandler, OnMaster {

	@NonNull
	protected final Node node;
	@NonNull
	protected final Computer computer;
	@NonNull
	protected final FilePath rootPath;

	public LnxNodeIsolationHandler(@NonNull final Node node) {
		Objects.requireNonNull(this.node = node);
		Objects.requireNonNull(this.computer = node.toComputer());
		Objects.requireNonNull(this.rootPath = this.node.getRootPath()
				.child("isolation-context"));
	}

	@Override
	public void firewallSetup() throws IOException, InterruptedException {
		final FilePath firewallPath = this.rootPath.child("nftables-python3.py");
		// deploy file from resources -> path
		// jar:/scripts/firewall/nftables-python3.py
		try (final InputStream input = getClass().getResourceAsStream("/scripts/firewall/nftables-python3.py")) {
			try (OutputStream out = firewallPath.write()) {
				org.apache.commons.io.IOUtils.copy(input, out);
			}
		}
	}

	@Override
	public <A extends IpAddress<A>> void firewallInputOpen(A host, A external, Set<Integer> ports) {
		// TODO Auto-generated method stub

	}

	@Override
	public <A extends IpAddress<A>> void firewallInputOpen(Map<A, Map<A, Set<Integer>>> data) {
		// TODO Auto-generated method stub

	}

	@Override
	public <A extends IpAddress<A>> void firewallInputRetainOpen(Map<A, Map<A, Set<Integer>>> data) {
		// TODO Auto-generated method stub

	}

	@Override
	public <A extends IpAddress<A>> void firewallInputCloseAll() {
		// TODO Auto-generated method stub

	}

	@Override
	public <A extends IpAddress<A>> void firewallInputCloseAll(A host) {
		// TODO Auto-generated method stub

	}

	@Override
	public <A extends IpAddress<A>> void firewallInputCloseAll(A host, A external) {
		// TODO Auto-generated method stub

	}

	@Override
	public <A extends IpAddress<A>> void firewallInputClose(A host, A external, Set<Integer> ports) {
		// TODO Auto-generated method stub

	}

	@Override
	public <A extends IpAddress<A>> void firewallInputClose(Map<A, Map<A, Set<Integer>>> data) {
		// TODO Auto-generated method stub

	}

}
