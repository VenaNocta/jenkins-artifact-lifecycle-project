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

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.isolationctx.FirewallDelegate;
import org.jenkinsci.plugins.isolationctx.config.IpUtils;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap;

import hudson.model.TaskListener;
import net.runeduniverse.lib.utils.net.api.IpAddress;
import net.runeduniverse.lib.utils.net.api.IpNetAddress;

public final class FirewallDelegateFilter implements FirewallDelegate {

	private final FirewallDelegate delegate;
	private final Set<IpAddress<?>> allowedHostIps;
	private final Set<IpNetAddress<?, ?>> allowedHostSubnets;
	private final Set<IpNetAddress<?, ?>> allowedExternalSubnets;

	public FirewallDelegateFilter(final FirewallDelegate firewallDelegate, //
			final Set<IpAddress<?>> allowedHostIps, final Set<IpNetAddress<?, ?>> allowedExternalSubnets) {
		Objects.requireNonNull(this.delegate = firewallDelegate);
		Objects.requireNonNull(allowedHostIps);
		Objects.requireNonNull(allowedExternalSubnets);

		this.allowedHostIps = allowedHostIps.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toUnmodifiableSet());
		this.allowedHostSubnets = allowedHostIps.stream()
				.map(IpUtils::toIpNetAddress)
				.filter(Objects::nonNull)
				.collect(Collectors.toUnmodifiableSet());
		this.allowedExternalSubnets = allowedExternalSubnets.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public void firewallInputOpen(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		this.delegate.firewallInputOpen(data.removeAll(this.allowedHostSubnets, this.allowedExternalSubnets), listener);
	}

	@Override
	public void firewallInputRetainOpen(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		this.delegate.firewallInputRetainOpen(data.removeAll(this.allowedHostSubnets, this.allowedExternalSubnets),
				listener);
	}

	@Override
	public void firewallInputClose(final TrafficMap data, final TaskListener listener)
			throws IOException, InterruptedException {
		this.delegate.firewallInputClose(data.removeAll(this.allowedHostSubnets, this.allowedExternalSubnets),
				listener);
	}

	@Override
	public void firewallInputCloseAll(final TaskListener listener) throws IOException, InterruptedException {
		for (IpAddress<?> hostIp : this.allowedHostIps) {
			this.delegate.firewallInputCloseAll(hostIp, listener);
		}
	}

	@Override
	public void firewallInputCloseAll(final IpAddress<?> host, final TaskListener listener)
			throws IOException, InterruptedException {
		if (this.allowedHostIps.contains(host))
			this.delegate.firewallInputCloseAll(host, listener);
	}

	@Override
	public void firewallInputCloseAll(final IpAddress<?> host, final IpAddress<?> external, final TaskListener listener)
			throws IOException, InterruptedException {
		if (this.allowedHostIps.contains(host) //
				&& this.allowedExternalSubnets.stream()
						.anyMatch(net -> net != null && net.contains(external)))
			this.delegate.firewallInputCloseAll(host, listener);
	}
}
