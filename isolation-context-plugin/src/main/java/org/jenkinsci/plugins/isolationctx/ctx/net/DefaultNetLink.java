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
package org.jenkinsci.plugins.isolationctx.ctx.net;

import org.jenkinsci.plugins.isolationctx.FirewallDelegate;
import org.jenkinsci.plugins.isolationctx.IsolationContext.NetLink;

import net.runeduniverse.lib.utils.net.IpNetworkUtils;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;

public class DefaultNetLink implements NetLink {

	private static final long serialVersionUID = 1L;

	protected final FirewallDelegate firewall;
	protected final IpNet4Address subnet4;
	protected final IpNet6Address subnet6;
	protected final Ip4Address hostAddr4;
	protected final Ip6Address hostAddr6;
	protected final Ip4Address contextAddr4;
	protected final Ip6Address contextAddr6;

	public DefaultNetLink(final FirewallDelegate firewall, //
			final IpNet4Address subnet4, final Ip4Address hostAddr4, final Ip4Address contextAddr4, //
			final IpNet6Address subnet6, final Ip6Address hostAddr6, final Ip6Address contextAddr6) {
		this.firewall = firewall;
		this.subnet4 = subnet4;
		this.hostAddr4 = hostAddr4;
		this.contextAddr4 = contextAddr4;
		this.subnet6 = subnet6;
		this.hostAddr6 = hostAddr6;
		this.contextAddr6 = contextAddr6;
	}

	@Override
	public FirewallDelegate firewall() {
		return this.firewall;
	}

	@Override
	public short getNetworkPrefixV4() {
		return this.subnet4 == null ? -1 : this.subnet4.getMask();
	}

	@Override
	public short getNetworkPrefixV6() {
		return this.subnet6 == null ? -1 : this.subnet6.getMask();
	}

	@Override
	public IpNet4Address getSubnetV4() {
		return this.subnet4;
	}

	@Override
	public IpNet6Address getSubnetV6() {
		return this.subnet6;
	}

	@Override
	public Ip4Address getHostAddressV4() {
		return this.hostAddr4;
	}

	@Override
	public Ip6Address getHostAddressV6() {
		return this.hostAddr6;
	}

	@Override
	public Ip4Address getContextAddressV4() {
		return this.contextAddr4;
	}

	@Override
	public Ip6Address getContextAddressV6() {
		return this.contextAddr6;
	}

	public static DefaultNetLink createLoopback(final FirewallDelegate firewall, final boolean ipv4,
			final boolean ipv6) {
		Ip4Address addr4 = null;
		IpNet4Address subnet4 = null;
		if (ipv4) {
			addr4 = IpNetworkUtils.createIp4Address(new byte[] { 0x7f, 0x00, 0x00, 0x01 });
			subnet4 = IpNetworkUtils.createIpNet4Address(addr4, (short) 32);
		}
		Ip6Address addr6 = null;
		IpNet6Address subnet6 = null;
		if (ipv6) {
			addr6 = IpNetworkUtils.createIp6Address(new byte[] { //
					0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
					0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 });
			subnet6 = IpNetworkUtils.createIpNet6Address(addr6, (short) 128);
		}
		return new DefaultNetLink(firewall, //
				subnet4, addr4, addr4, //
				subnet6, addr6, addr6);
	}
}