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
package org.jenkinsci.plugins.repoproxy.maven;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.isolationctx.config.IpUtils;
import org.jenkinsci.plugins.repoproxy.AProxyBootstrapper;
import org.jenkinsci.plugins.repoproxy.ProxyBootstrapper;
import org.jenkinsci.plugins.repoproxy.maven.config.MvnProxyConfig;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.model.Action;
import hudson.model.TaskListener;
import jenkins.agents.ControllerToAgentFileCallable;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class MvnProxyBootstrapper extends AProxyBootstrapper implements ProxyBootstrapper, Action {

	protected final Map<String, MvnProxyConfig> instConfigs;
	protected final Set<IpAddress<?>> ipSet;
	protected final int port;

	public MvnProxyBootstrapper(final Set<IpAddress<?>> ipSet, final int port) {
		this.instConfigs = new LinkedHashMap<>();
		this.ipSet = ipSet;
		this.port = port;
	}

	protected MvnProxyBootstrapper(final Map<String, MvnProxyConfig> instanceConfigs, final Set<IpAddress<?>> ipSet,
			final int port) {
		this.instConfigs = instanceConfigs;
		this.ipSet = ipSet;
		this.port = port;
	}

	public boolean canSupport(final String refId, final Set<IpAddress<?>> ipSet, final int port) {
		if (this.instConfigs.containsKey(refId))
			return false;
		if (this.ipSet.size() != ipSet.size() || !this.ipSet.containsAll(ipSet))
			return false;
		if (this.port != port)
			return false;
		return true;
	}

	public void addRepoInstance(final String refId, final MvnProxyConfig config) {
		this.instConfigs.put(refId, config);
	}

	@Override
	public ControllerToAgentFileCallable<UUID> serverStartCallable(//
			final StepContext context, final Map<String, String> proxyEnv, final TaskListener listener)
			throws IOException, InterruptedException {

		final Set<InetAddress> addressSet = this.ipSet.stream()
				.map(ip -> ip.toInetAddress(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		final FlowNode node = context.get(FlowNode.class);
		final MvnNettyStartCallable callable = new MvnNettyStartCallable(//
				node.getId(), this.instConfigs, addressSet, this.port);

		updateEnv(this.instConfigs.keySet(), this.ipSet, this.port, proxyEnv, "MVN");

		return callable;
	}

	@Override
	public final String getIconFileName() {
		return null;
	}

	@Override
	public final String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return null;
	}

	// XStream2 hook -> handles marshal on save()
	private Object writeReplace() {
		return Snapshot.marshal(this);
	}

	public static class Snapshot {

		protected final Map<String, MvnProxyConfig> instConfigs;
		protected final Set<String> ip4Set;
		protected final Set<String> ip6Set;
		protected final int port;

		protected Snapshot(final Map<String, MvnProxyConfig> instConfigs, final Set<String> ip4Set,
				final Set<String> ip6Set, final int port) {
			this.instConfigs = instConfigs;
			this.ip4Set = ip4Set;
			this.ip6Set = ip6Set;
			this.port = port;
		}

		// XStream2 hook -> handles unmarshal on load()
		private Object readResolve() {
			final Set<IpAddress<?>> ips = new HashSet<>();
			ips.add((IpAddress<?>) IpUtils.unmarshalCol(HashSet::new, this.ip4Set, IpUtils.IP4_PARSER));
			ips.add((IpAddress<?>) IpUtils.unmarshalCol(HashSet::new, this.ip4Set, IpUtils.IP6_PARSER));

			final MvnProxyBootstrapper inst = new MvnProxyBootstrapper(this.instConfigs, ips, this.port);
			return inst;
		}

		public static Snapshot marshal(final MvnProxyBootstrapper instance) {
			final Set<String> ip4Set = new HashSet<>();
			final Set<String> ip6Set = new HashSet<>();
			for (IpAddress<?> ip : instance.ipSet) {
				if (ip instanceof Ip4Address)
					ip4Set.add(ip.toCidrNotation());
				else if (ip instanceof Ip6Address)
					ip6Set.add(ip.toCidrNotation());
			}
			return new Snapshot(instance.instConfigs, ip4Set, ip6Set, instance.port);
		}
	}
}
