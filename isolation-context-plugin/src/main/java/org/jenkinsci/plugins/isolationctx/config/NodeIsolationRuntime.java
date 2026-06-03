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
package org.jenkinsci.plugins.isolationctx.config;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.isolationctx.handler.NodeIsolationHandler;

import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import net.runeduniverse.lib.utils.net.api.IpAddress;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;

public interface NodeIsolationRuntime {

	public Node node();

	public NodeIsolationHandler isolationHandler();

	public IpNet4Address getIpv4Subnet();

	public IpNet6Address getIpv6Subnet();

	public void setIpv4Subnet(String value) throws UnknownHostException;

	public void setIpv4Subnet(IpNet4Address subnet) throws UnknownHostException;

	public void setIpv6Subnet(String value) throws UnknownHostException;

	public void setIpv6Subnet(IpNet6Address subnet) throws UnknownHostException;

	public void setup(TaskListener listener) throws IOException, InterruptedException;

	public IpNet4Address reserveIpv4Subnet(Run<?, ?> run);

	public IpNet6Address reserveIpv6Subnet(Run<?, ?> run);

	public void freeIpv4Subnet(IpNet4Address subnet);

	public void freeIpv6Subnet(IpNet6Address subnet);

	public void freeAllSubnetsOf(String extId);

	public void firewallInputOpen(IpAddress<?> host, IpAddress<?> external, Set<Integer> ports, TaskListener listener)
			throws IOException, InterruptedException;

	public void firewallInputOpen(Map<IpAddress<?>, Map<IpAddress<?>, Set<Integer>>> data, TaskListener listener)
			throws IOException, InterruptedException;

	public void firewallInputRetainOpen(Map<IpAddress<?>, Map<IpAddress<?>, Set<Integer>>> data, TaskListener listener)
			throws IOException, InterruptedException;

	public void firewallInputCloseAll(TaskListener listener) throws IOException, InterruptedException;

	public void firewallInputCloseAll(IpAddress<?> host, TaskListener listener)
			throws IOException, InterruptedException;

	public void firewallInputCloseAll(IpAddress<?> host, IpAddress<?> external, TaskListener listener)
			throws IOException, InterruptedException;

	public void firewallInputClose(IpAddress<?> host, IpAddress<?> external, Set<Integer> ports, TaskListener listener)
			throws IOException, InterruptedException;

	public void firewallInputClose(Map<IpAddress<?>, Map<IpAddress<?>, Set<Integer>>> data, TaskListener listener)
			throws IOException, InterruptedException;
}
