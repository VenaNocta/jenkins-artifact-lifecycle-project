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
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.maven.config.Maven2Repository;
import org.jenkinsci.plugins.isolationctx.IsolationContext.NetLink;
import org.jenkinsci.plugins.isolationctx.config.IpUtils;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap.Protocol;
import org.jenkinsci.plugins.repoproxy.AProxyBootstrapper;
import org.jenkinsci.plugins.repoproxy.config.BomSourceCache;
import org.jenkinsci.plugins.repoproxy.maven.config.MvnProxyConfig;
import org.jenkinsci.plugins.repoproxy.steps.TrackedArtifactsAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.common.IdCredentials;

import hudson.model.Action;
import hudson.model.TaskListener;
import jenkins.agents.ControllerToAgentFileCallable;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class MvnProxyBootstrapper extends AProxyBootstrapper implements Action {

	protected final Map<String, MvnProxyConfig> instConfigs;
	protected final Set<IpAddress<?>> ipSet;
	protected final int port;
	// tracking data
	protected final Map<String, Set<String>> _purls = new ConcurrentHashMap<>();

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
	public Set<UUID> getRequiredBoms() {
		final Set<UUID> ids = new LinkedHashSet<>();
		this.instConfigs.values()
				.stream()
				.map(MvnProxyConfig::getRequiredBoms)
				.forEach(ids::addAll);
		return ids;
	}

	@Override
	public Set<UUID> getRequiredRepos() {
		final Set<UUID> ids = new LinkedHashSet<>();
		this.instConfigs.values()
				.stream()
				.map(MvnProxyConfig::getRequiredRepos)
				.forEach(ids::addAll);
		return ids;
	}

	@Override
	public Set<String> getRequiredCredentials() {
		final Set<String> creds = new LinkedHashSet<>();
		this.instConfigs.values()
				.stream()
				.map(MvnProxyConfig::getRequiredCredentials)
				.forEach(creds::addAll);
		return creds;
	}

	@Override
	public ControllerToAgentFileCallable<UUID> serverStartCallable(final NetLink link, //
			final StepContext context, final Map<String, String> proxyEnv, final Map<UUID, BomSourceCache> bomCacheMap,
			final Map<UUID, ArtifactRepository> repoMap, final Map<String, IdCredentials> credentialMap,
			final TaskListener listener) throws IOException, InterruptedException {

		final Set<InetAddress> addressSet = this.ipSet.stream()
				.map(ip -> ip.toInetAddress(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		final Map<UUID, Maven2Repository> repoMap2 = repoMap.values()
				.stream()
				.filter(Maven2Repository::isSupported)
				.collect(Collectors.toMap(ArtifactRepository::getUuid, r -> (Maven2Repository) r));

		final FlowNode node = context.get(FlowNode.class);
		final MvnNettyStartCallable callable = new MvnNettyStartCallable(//
				node.getId(), this.instConfigs, bomCacheMap, repoMap2, credentialMap, addressSet, this.port);

		updateEnv(this.instConfigs.keySet(), this.ipSet, this.port, proxyEnv, "MVN");

		for (IpAddress<?> ip : this.ipSet) {
			// the TrafficMap only allows ip pairs with the same family
			// -> all others are ignored
			link.firewall()
					.firewallInputOpen(
							new TrafficMap().add(ip, link.getContextAddressV4(), Protocol.TCP, Set.of(this.port))
									.add(ip, link.getContextAddressV6(), Protocol.TCP, Set.of(this.port)),
							listener);
		}

		return callable;
	}

	protected void trackArtifact(final String refKey, final String purl) {
		this._purls.computeIfAbsent(refKey, k -> new ConcurrentSkipListSet<>())
				.add(purl);
	}

	protected void trackArtifacts(final String refKey, final Collection<String> purls) {
		this._purls.computeIfAbsent(refKey, k -> new ConcurrentSkipListSet<>())
				.addAll(purls);
	}

	@Override
	public void receiveCallback(final String refKey, final Serializable data) {
		if (data instanceof ArtifactRequestTracker.Data artifact) {
			trackArtifact(refKey, artifact.getPurl());
		}
	}

	@Override
	public Set<Action> results() {
		final Set<Action> set = new LinkedHashSet<>();
		final TrackedArtifactsAction tracker = new TrackedArtifactsAction();
		this._purls.forEach(tracker::addArtifacts);
		set.add(tracker);
		return set;
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
		// tracking data
		protected final Map<String, Set<String>> _purls = new LinkedHashMap<>();

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
			this._purls.forEach(inst::trackArtifacts);
			return inst;
		}

		protected void marshalTrackedArtifacts(final String refKey, final Collection<String> purls) {
			this._purls.computeIfAbsent(refKey, k -> new LinkedHashSet<>())
					.addAll(purls);
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
			final Snapshot snapshot = new Snapshot(instance.instConfigs, ip4Set, ip6Set, instance.port);
			instance._purls.forEach(snapshot::marshalTrackedArtifacts);
			return snapshot;
		}
	}
}
