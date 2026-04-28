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
package org.jenkinsci.plugins.airgap.config;

import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;

import hudson.model.Node;
import hudson.model.Run;
import hudson.slaves.NodeProperty;
import net.runeduniverse.lib.utils.net.IpNetworkUtils;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;
import net.runeduniverse.lib.utils.net.api.IpNetAddress;

// stores data required for per Run Context Isolation on this Node!
public class LnxNodeIsolationProperty extends NodeProperty<Node> {

	protected transient final Object lock4 = new Object();
	protected transient final Object lock6 = new Object();

	protected final SortedSet<IpNet4Address> subnet4Stack = //
			new TreeSet<>(IpNetAddress.compareByLowestAddress());
	protected final Map<IpNet4Address, String> subnet4Reservations = //
			new LinkedHashMap<>();

	protected IpNet4Address subnet4 = //
			IpNetworkUtils.createIpNet4Address(new byte[] { 0x7f, 0x14, 0x00, 0x01 }, (short) 16);
	protected IpNet6Address subnet6 = null;

	public IpNet4Address getIpv4Subnet() {
		return this.subnet4;
	}

	public IpNet6Address getIpv6Subnet() {
		return this.subnet6;
	}

	public void setIpv4Subnet(final String value) throws UnknownHostException {
		synchronized (this.lock4) {
			this.subnet4 = IpNetworkUtils.parseIpNet4Address(value);
		}
	}

	public void setIpv4Subnet(final IpNet4Address subnet) throws UnknownHostException {
		synchronized (this.lock4) {
			this.subnet4 = subnet;
		}
	}

	public void setIpv6Subnet(final String value) throws UnknownHostException {
		synchronized (this.lock6) {
			this.subnet6 = IpNetworkUtils.parseIpNet6Address(value);
		}
	}

	public void setIpv6Subnet(final IpNet6Address subnet) throws UnknownHostException {
		synchronized (this.lock6) {
			this.subnet6 = subnet;
		}
	}

	public IpNet4Address reserveIpv4Subnet(final Run<?, ?> run) {
		if (!run.isInProgress() || this.subnet4 == null)
			return null;

		synchronized (this.lock4) {
			final String id = run.getExternalizableId();
			// find first known subnet in stack that is not used
			IpNet4Address net = this.subnet4Stack.stream()
					.filter(((Predicate<IpNet4Address>) this.subnet4Reservations::containsKey).negate())
					.findFirst()
					.orElse(null);
			if (net == null) {
				// if none is unused let's see if another can be allocated!
				final Ip4Address lastSubnetAddr = //
						this.subnet4Stack.isEmpty() ? null
								: this.subnet4Stack.last()
										.getBaseAddress();
				final Iterator<IpNet4Address> i = this.subnet4.splitForMask(lastSubnetAddr, (short) 30, 1)
						.iterator();
				if (i.hasNext())
					net = i.next();
			}
			if (net == null) {
				// no available subnets!
				return null;
			}
			// reserve subnet
			this.subnet4Reservations.put(net, id);
			return net;
		}
	}

	public IpNet6Address reserveIpv6Subnet(final Run<?, ?> run) {
		if (!run.isInProgress() || this.subnet6 == null)
			return null;

		synchronized (this.lock6) {
			// TODO implement
			return null;
		}
	}

	public void freeIpv4Subnet(final IpNet4Address subnet) {
		synchronized (this.lock4) {
			this.subnet4Reservations.remove(subnet);
		}
	}

	public void freeIpv6Subnet(final IpNet6Address subnet) {
		synchronized (this.lock6) {
			// TODO implement
		}
	}

	public void freeAllSubnetsOf(final String extId) {
		synchronized (this.lock4) {
			for (Iterator<Entry<IpNet4Address, String>> i = this.subnet4Reservations.entrySet()
					.iterator(); i.hasNext();) {
				final Entry<IpNet4Address, String> entry = i.next();
				if (extId.equals(entry.getValue()))
					i.remove();
			}
		}
	}
}
