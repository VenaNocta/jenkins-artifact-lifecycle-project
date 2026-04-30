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
package org.jenkinsci.plugins.ctx.isolation.config;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.InvisibleAction;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import net.runeduniverse.lib.utils.net.IpNetworkUtils;
import net.runeduniverse.lib.utils.net.api.CidrObject;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.IpAddress;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;
import net.runeduniverse.lib.utils.net.api.IpNetAddress;

// stores data required for per Run Context Isolation on this Node!
public class NodeIsolationRuntimeAction extends InvisibleAction implements Saveable, Serializable {

	private static final long serialVersionUID = 1L;

	protected transient final Object lock4 = new Object();
	protected transient final Object lock6 = new Object();

	protected transient Node node;

	protected final SortedSet<IpNet4Address> subnet4Stack;
	protected final Map<IpNet4Address, String> subnet4Reservations;

	protected IpNet4Address subnet4;
	protected IpNet6Address subnet6;

	public NodeIsolationRuntimeAction(final Node owner) {
		this(owner,
				// Ipv4
				IpNetworkUtils.createIpNet4Address(new byte[] { 0x7f, 0x14, 0x00, 0x01 }, (short) 16), new TreeSet<>(),
				new LinkedHashMap<>(), //
				// Ipv6
				null);
	}

	protected NodeIsolationRuntimeAction(final Node node, //
			final IpNet4Address subnet4, final SortedSet<IpNet4Address> subnet4Stack,
			final Map<IpNet4Address, String> subnet4Reservations, //
			final IpNet6Address subnet6) {
		this.node = node;
		// Ipv4
		this.subnet4 = subnet4;
		this.subnet4Stack = subnet4Stack;
		this.subnet4Reservations = subnet4Reservations;
		// Ipv6
		this.subnet6 = subnet6;
	}

	public Node node() {
		return this.node;
	}

	public IpNet4Address getIpv4Subnet() {
		return this.subnet4;
	}

	public IpNet6Address getIpv6Subnet() {
		return this.subnet6;
	}

	public void setIpv4Subnet(final String value) throws UnknownHostException {
		synchronized (this.lock4) {
			this.subnet4 = IpNetworkUtils.parseIpNet4Address(value);
			trySave();
		}
	}

	public void setIpv4Subnet(final IpNet4Address subnet) throws UnknownHostException {
		synchronized (this.lock4) {
			this.subnet4 = subnet;
			trySave();
		}
	}

	public void setIpv6Subnet(final String value) throws UnknownHostException {
		synchronized (this.lock6) {
			this.subnet6 = IpNetworkUtils.parseIpNet6Address(value);
			trySave();
		}
	}

	public void setIpv6Subnet(final IpNet6Address subnet) throws UnknownHostException {
		synchronized (this.lock6) {
			this.subnet6 = subnet;
			trySave();
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
			this.subnet4Stack.add(net);
			this.subnet4Reservations.put(net, id);
			trySave();
			return net;
		}
	}

	public IpNet6Address reserveIpv6Subnet(final Run<?, ?> run) {
		if (!run.isInProgress() || this.subnet6 == null)
			return null;

		synchronized (this.lock6) {
			// TODO implement
			trySave();
			return null;
		}
	}

	public void freeIpv4Subnet(final IpNet4Address subnet) {
		synchronized (this.lock4) {
			this.subnet4Reservations.remove(subnet);
			trySave();
		}
	}

	public void freeIpv6Subnet(final IpNet6Address subnet) {
		synchronized (this.lock6) {
			// TODO implement
			trySave();
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
			trySave();
		}
	}

	protected void trySave() {
		try {
			save();
		} catch (IOException ignored) {
			ignored.printStackTrace();
		}
	}

	@Override
	public void save() throws IOException {
		final XmlFile configFile = getConfigFile(this.node);
		configFile.write(this);
		SaveableListener.fireOnChange(this, configFile);
	}

	// XStream2 hook -> handles marshal on save()
	private Object writeReplace() {
		return NodeIsolationRuntimeSnapshot.marshal(this);
	}

	protected static XmlFile getConfigFile(final Node node) {
		return new XmlFile(new File(node.getRootDir(), "ctx-isolation.xml"));
	}

	protected static NodeIsolationRuntimeAction load(final Node node) throws IOException {
		final XmlFile f = getConfigFile(node);
		if (!f.exists())
			return new NodeIsolationRuntimeAction(node);

		final NodeIsolationRuntimeAction action = (NodeIsolationRuntimeAction) f.read();
		action.node = node;
		return action;
	}

	public static NodeIsolationRuntimeAction get(final Node node) throws IOException {
		if (node == null)
			return null;
		final Computer computer = node.toComputer();
		if (computer == null)
			return null;
		NodeIsolationRuntimeAction action = computer.getAction(NodeIsolationRuntimeAction.class);
		if (action == null)
			computer.addAction(action = load(node));
		return action;
	}

	public static class NodeIsolationRuntimeSnapshot {

		protected final String subnet4;
		protected final String subnet6;

		protected final List<String> subnet4Stack;
		protected final Map<String, String> subnet4Reservations;

		public NodeIsolationRuntimeSnapshot(//
				final String subnet4, final List<String> subnet4Stack, final Map<String, String> subnet4Reservations, //
				final String subnet6) {
			this.subnet4 = subnet4;
			this.subnet4Stack = subnet4Stack;
			this.subnet4Reservations = subnet4Reservations;
			this.subnet6 = subnet6;
		}

		// XStream2 hook -> handles unmarshal on load()
		private Object readResolve() {
			return new NodeIsolationRuntimeAction(null, //
					// Ipv4
					IPNET4_PARSER.apply(this.subnet4), unmarshal(TreeSet::new, this.subnet4Stack, IPNET4_PARSER),
					unmarshal(LinkedHashMap::new, this.subnet4Reservations, IPNET4_PARSER),
					// Ipv6
					IPNET6_PARSER.apply(this.subnet6));
		}

		public static NodeIsolationRuntimeSnapshot marshal(final NodeIsolationRuntimeAction prop) {
			return new NodeIsolationRuntimeSnapshot(//
					// Ipv4
					prop.subnet4 == null ? null : prop.subnet4.toCidrNotation(), marshal(prop.subnet4Stack),
					marshal(prop.subnet4Reservations),
					// Ipv6
					prop.subnet6 == null ? null : prop.subnet6.toCidrNotation());
		}

		public static final Function<String, IpNet4Address> IPNET4_PARSER = IpNetworkUtils::tryParseIpNet4Address;
		public static final Function<String, IpNet6Address> IPNET6_PARSER = IpNetworkUtils::tryParseIpNet6Address;

		public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>> List<String> marshal(
				final Collection<N> col) {

			if (col == null || col.isEmpty())
				return Collections.emptyList();

			return col.stream()
					.map(CidrObject::toCidrNotation)
					.toList();
		}

		public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>, C extends Collection<N>> C unmarshal(
				final Supplier<C> supplier, final List<String> data, final Function<String, N> parser) {

			if (data == null || data.isEmpty())
				return supplier.get();

			return data.stream()
					.map(parser)
					.filter(Objects::nonNull)
					.collect(Collectors.toCollection(supplier));
		}

		public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>> Map<String, String> marshal(
				final Map<N, String> map) {

			if (map == null || map.isEmpty())
				return Collections.emptyMap();

			return map.entrySet()
					.stream()
					.collect(Collectors.toMap(e -> e.getKey()
							.toCidrNotation(), Entry::getValue));
		}

		public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>, M extends Map<N, String>> M unmarshal(
				final Supplier<M> supplier, final Map<String, String> data, final Function<String, N> parser) {

			if (data == null || data.isEmpty())
				return supplier.get();

			return data.entrySet()
					.stream()
					.collect(Collectors.toMap(e -> parser.apply(e.getKey()), Entry::getValue, (a, b) -> b, supplier));
		}
	}
}
