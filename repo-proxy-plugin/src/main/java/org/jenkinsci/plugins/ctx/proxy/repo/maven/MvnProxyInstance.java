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
package org.jenkinsci.plugins.ctx.proxy.repo.maven;

import java.io.PrintStream;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.jenkinsci.plugins.ctx.isolation.config.IpUtils;
import org.jenkinsci.plugins.ctx.proxy.repo.AProxyInstance;
import org.jenkinsci.plugins.ctx.proxy.repo.ProxyInstance;

import hudson.model.Action;
import hudson.model.TaskListener;
import io.netty.channel.Channel;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;
import net.runeduniverse.lib.repo.maven.data.DefaultFileTypeIndex;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class MvnProxyInstance extends AProxyInstance implements ProxyInstance, Action {

	protected final Set<String> refIds;
	protected final Set<IpAddress<?>> ipSet;
	protected final int port;

	protected transient Deque<Channel> openChannels = new ConcurrentLinkedDeque<>();
	protected transient ProxyServer server = null;

	public MvnProxyInstance(final Set<IpAddress<?>> ipSet, final int port) {
		this.refIds = new HashSet<>();
		this.ipSet = ipSet;
		this.port = port;

		init();
	}

	protected MvnProxyInstance(final Set<String> refIds, final Set<IpAddress<?>> ipSet, final int port) {
		this.refIds = refIds;
		this.ipSet = ipSet;
		this.port = port;
	}

	protected void init() {
		this.server = new ProxyServer(new DefaultFileTypeIndex(), HttpRepoServerInitializer::new);

		// TODO rehydrate proxy-instances
	}

	public boolean canSupport(final String refId, final Set<IpAddress<?>> ipSet, final int port) {
		if (this.refIds.contains(refId))
			return false;
		if (this.ipSet.size() != ipSet.size() || !this.ipSet.containsAll(ipSet))
			return false;
		if (this.port != port)
			return false;
		return true;
	}

	public void addRepoInstance(final String refId) {
		this.refIds.add(refId);

		MavenRepositoryInstance instance = null;
		// this.server.addInstance(instance);
	}

	@Override
	public void start(final Map<String, String> proxyEnv, final TaskListener listener) {
		final PrintStream log = listener.getLogger();
		if (this.stopFuture.isDone()) {
			log.println("[ RepoProxy ] Maven Proxy start skipped, already stopped!");
			return;
		}

		for (IpAddress<?> ip : this.ipSet) {
			try {
				this.openChannels.add(this.server.bindChannel(ip.toInetAddress(null), this.port)
						.sync()
						.channel());
			} catch (InterruptedException e) {
				e.printStackTrace(log);
			}
		}

		updateEnv(this.refIds, this.ipSet, this.port, proxyEnv, "MVN");
	}

	@Override
	public void stop() {
		for (Channel channel : this.openChannels) {
			channel.close();
		}
		announceStop();
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

		protected final Set<String> refIds;
		protected final Set<String> ip4Set;
		protected final Set<String> ip6Set;
		protected final int port;

		protected Snapshot(final Set<String> refIds, final Set<String> ip4Set, final Set<String> ip6Set,
				final int port) {
			this.refIds = refIds;
			this.ip4Set = ip4Set;
			this.ip6Set = ip6Set;
			this.port = port;
		}

		// XStream2 hook -> handles unmarshal on load()
		private Object readResolve() {
			final Set<IpAddress<?>> ips = new HashSet<>();
			ips.add((IpAddress<?>) IpUtils.unmarshalCol(HashSet::new, this.ip4Set, IpUtils.IP4_PARSER));
			ips.add((IpAddress<?>) IpUtils.unmarshalCol(HashSet::new, this.ip4Set, IpUtils.IP6_PARSER));

			final MvnProxyInstance inst = new MvnProxyInstance(this.refIds, ips, this.port);
			inst.init();
			return inst;
		}

		public static Snapshot marshal(final MvnProxyInstance instance) {
			final Set<String> ip4Set = new HashSet<>();
			final Set<String> ip6Set = new HashSet<>();
			for (IpAddress<?> ip : instance.ipSet) {
				if (ip instanceof Ip4Address)
					ip4Set.add(ip.toCidrNotation());
				else if (ip instanceof Ip6Address)
					ip6Set.add(ip.toCidrNotation());
			}
			return new Snapshot(instance.refIds, ip4Set, ip6Set, instance.port);
		}
	}
}
