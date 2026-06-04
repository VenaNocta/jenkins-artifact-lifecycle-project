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
package org.jenkinsci.plugins.isolationctx.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.isolationctx.config.IpUtils;
import java.util.Set;
import hudson.Util;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class TrafficMap implements Serializable {

	public static enum Protocol {
		TCP("tcp"), UDP("tcp");

		protected final String text;

		private Protocol(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return this.text;
		}

		public static String toString(final Protocol protocol) {
			if (protocol == null)
				return null;
			return protocol.toString();
		}

		public static Protocol fromString(String text) {
			if ((text = Util.fixEmptyAndTrim(text)) == null)
				return null;
			final Protocol[] entries = Protocol.values();
			for (int i = 0; i < entries.length; i++) {
				if (entries[i].text.equalsIgnoreCase(text))
					return entries[i];
			}
			return null;
		}
	}

	private static final long serialVersionUID = 1L;

	public static final Class<?>[] SUPPORTED_IP_TYPES = new Class<?>[] { Ip4Address.class, Ip6Address.class };

	protected final boolean forceSameIpType;
	protected final Map<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> data;

	public TrafficMap() {
		this(true);
	}

	public TrafficMap(final boolean forceSameIpType) {
		this.forceSameIpType = forceSameIpType;
		this.data = new LinkedHashMap<>();
	}

	protected TrafficMap(final boolean forceSameIpType,
			final Map<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> data) {
		this.forceSameIpType = forceSameIpType;
		this.data = data;
	}

	// XStream2 hook -> handles marshal on save()
	private Object writeReplace() {
		return TrafficMapSnapshot.marshal(this);
	}

	public void add(final IpAddress<?> ipA, IpAddress<?> ipB, Protocol protocol, Set<Integer> ports) {
		final Map<Protocol, Set<Integer>> portMap = new LinkedHashMap<>();
		portMap.put(protocol, ports);
		add(ipA, ipB, portMap);
	}

	public void add(final IpAddress<?> ipA, IpAddress<?> ipB, Map<Protocol, Set<Integer>> portMap) {
		final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = new LinkedHashMap<>();
		innerMap.put(ipB, portMap);
		add(ipA, innerMap);
	}

	public void add(final IpAddress<?> ipA, Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap) {
		final Map<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> data = new LinkedHashMap<>();
		data.put(ipA, innerMap);
		add(data);
	}

	public void add(final TrafficMap trafficMap) {
		if (trafficMap == null)
			return;
		add(trafficMap.data);
	}

	public void add(final Map<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> dataMap) {
		if (dataMap == null || dataMap.isEmpty())
			return;
		for (Entry<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> dataEntry : dataMap.entrySet()) {
			// inner map
			final IpAddress<?> ipA = dataEntry.getKey();
			final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = dataEntry.getValue();
			if (ipA == null || innerMap == null || innerMap.isEmpty())
				continue;

			for (Entry<IpAddress<?>, Map<Protocol, Set<Integer>>> innerEntry : innerMap.entrySet()) {
				final IpAddress<?> ipB = innerEntry.getKey();
				final Map<Protocol, Set<Integer>> protoMap = innerEntry.getValue();
				// protocol map
				if (ipB == null || protoMap == null || protoMap.isEmpty())
					continue;

				for (Entry<Protocol, Set<Integer>> entry : protoMap.entrySet()) {
					final Protocol protocol = entry.getKey();
					final Set<Integer> ports = entry.getValue();
					// ports
					if (protocol == null || ports == null)
						continue;
					removeInvalidPorts(ports);
					if (ports.isEmpty() || this.forceSameIpType && getIpType(ipA.getClass(), ipB.getClass()) == null)
						continue;

					this.data.computeIfAbsent(ipA, k -> new LinkedHashMap<>())
							.computeIfAbsent(ipB, k -> new LinkedHashMap<>())
							.computeIfAbsent(protocol, k -> new LinkedHashSet<>())
							.addAll(ports);
				}
			}
		}
	}

	public void remove(final IpAddress<?> ipA, IpAddress<?> ipB, Protocol protocol, Set<Integer> ports) {
		final Map<Protocol, Set<Integer>> portMap = new LinkedHashMap<>();
		portMap.put(protocol, ports);
		remove(ipA, ipB, portMap);
	}

	public void remove(final IpAddress<?> ipA, IpAddress<?> ipB, Map<Protocol, Set<Integer>> portMap) {
		final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = new LinkedHashMap<>();
		innerMap.put(ipB, portMap);
		remove(ipA, innerMap);
	}

	public void remove(final IpAddress<?> ipA, Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap) {
		final Map<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> data = new LinkedHashMap<>();
		data.put(ipA, innerMap);
		remove(data);
	}

	public void remove(final TrafficMap trafficMap) {
		if (trafficMap == null)
			return;
		remove(trafficMap.data);
	}

	public void remove(final Map<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> dataMap) {
		if (dataMap == null || dataMap.isEmpty())
			return;
		for (Entry<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> dataEntry : dataMap.entrySet()) {
			// inner map
			final IpAddress<?> ipA = dataEntry.getKey();
			final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = dataEntry.getValue();
			if (ipA == null || innerMap == null || innerMap.isEmpty())
				continue;

			for (Entry<IpAddress<?>, Map<Protocol, Set<Integer>>> innerEntry : innerMap.entrySet()) {
				final IpAddress<?> ipB = innerEntry.getKey();
				final Map<Protocol, Set<Integer>> protoMap = innerEntry.getValue();
				// protocol map
				if (ipB == null || protoMap == null || protoMap.isEmpty())
					continue;

				for (Entry<Protocol, Set<Integer>> entry : protoMap.entrySet()) {
					final Protocol protocol = entry.getKey();
					final Set<Integer> ports = entry.getValue();
					// ports
					if (protocol == null || ports == null || ports.isEmpty())
						continue;

					this.data.computeIfAbsent(ipA, k -> new LinkedHashMap<>())
							.computeIfAbsent(ipB, k -> new LinkedHashMap<>())
							.computeIfAbsent(protocol, k -> new LinkedHashSet<>())
							.removeAll(ports);
				}
			}
		}
	}

	public Map<IpAddress<?>, Map<Protocol, Set<Integer>>> removeAll(final IpAddress<?> ipA) {
		final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = this.data.remove(ipA);
		return innerMap == null ? Collections.emptyMap() : innerMap;
	}

	public Map<Protocol, Set<Integer>> removeAll(final IpAddress<?> ipA, final IpAddress<?> ipB) {
		final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = this.data.get(ipA);
		if (innerMap == null)
			return Collections.emptyMap();
		final Map<Protocol, Set<Integer>> protoMap = innerMap.remove(ipA);
		return protoMap == null ? Collections.emptyMap() : protoMap;
	}

	public Set<Integer> removeAll(final IpAddress<?> ipA, final IpAddress<?> ipB, final Protocol protocol) {
		final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = this.data.get(ipA);
		if (innerMap == null)
			return Collections.emptySet();
		final Map<Protocol, Set<Integer>> protoMap = innerMap.get(ipB);
		if (protoMap == null)
			return Collections.emptySet();
		final Set<Integer> ports = protoMap.remove(protocol);
		return ports == null ? Collections.emptySet() : ports;
	}

	public void clear() {
		this.data.clear();
	}

	public void forEach(final EntryConsumer<IpAddress<?>> consumer) {
		if (consumer == null)
			return;
		for (Entry<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> dataEntry : this.data.entrySet()) {
			// inner map
			final IpAddress<?> ipA = dataEntry.getKey();
			final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = dataEntry.getValue();
			if (innerMap.isEmpty())
				continue;

			for (Entry<IpAddress<?>, Map<Protocol, Set<Integer>>> innerEntry : innerMap.entrySet()) {
				final IpAddress<?> ipB = innerEntry.getKey();
				// copy map
				final Map<Protocol, Set<Integer>> protoMap = innerEntry.getValue()
						.entrySet()
						.stream()
						.collect(Collectors.toMap(Map.Entry::getKey, e -> new HashSet<>(e.getValue())));
				// protocol map
				consumer.accept(ipA, ipB, protoMap);
			}
		}
	}

	public <A extends IpAddress<?>> void forEach(final Class<A> type, final EntryConsumer<A> consumer) {
		if (type == null || consumer == null)
			return;
		for (Entry<IpAddress<?>, Map<IpAddress<?>, Map<Protocol, Set<Integer>>>> dataEntry : this.data.entrySet()) {
			// inner map
			final IpAddress<?> ipA = dataEntry.getKey();
			final Map<IpAddress<?>, Map<Protocol, Set<Integer>>> innerMap = dataEntry.getValue();
			if (innerMap.isEmpty())
				continue;

			for (Entry<IpAddress<?>, Map<Protocol, Set<Integer>>> innerEntry : innerMap.entrySet()) {
				final IpAddress<?> ipB = innerEntry.getKey();
				// copy map
				final Map<Protocol, Set<Integer>> protoMap = innerEntry.getValue()
						.entrySet()
						.stream()
						.collect(Collectors.toMap(Map.Entry::getKey, e -> new HashSet<>(e.getValue())));
				// protocol map
				if (type.isAssignableFrom(getIpType(ipA.getClass(), ipB.getClass())))
					consumer.accept((A) ipA, (A) ipB, protoMap);
			}
		}
	}

	protected void removeInvalidPorts(final Set<Integer> ports) {
		for (Iterator<Integer> i = ports.iterator(); i.hasNext();) {
			final Integer port = i.next();
			if (port == null || port < 1 || 65535 < port)
				i.remove();
		}
	}

	protected <A extends IpAddress<?>, B extends IpAddress<?>, R extends IpAddress<?>> Class<R> getIpType(
			final Class<A> typeA, final Class<B> typeB) {
		for (Class<?> type : SUPPORTED_IP_TYPES) {
			if (type.isAssignableFrom(typeA) && type.isAssignableFrom(typeB))
				return (Class<R>) type;
		}
		return null;
	}

	@FunctionalInterface
	public static interface EntryConsumer<T> {
		public void accept(T a, T b, Map<Protocol, Set<Integer>> protoMap);
	}

	public static class TrafficMapSnapshot {
		protected final boolean forceSameIpType;
		// ipA : ipB : protocol : ports
		protected final Map<String, Map<String, Map<String, Set<Integer>>>> data;

		public TrafficMapSnapshot(final boolean forceSameIpType,
				final Map<String, Map<String, Map<String, Set<Integer>>>> data) {
			this.forceSameIpType = forceSameIpType;
			this.data = data;
		}

		// XStream2 hook -> handles unmarshal on load()
		private Object readResolve() {
			return new TrafficMap(this.forceSameIpType, this.data.entrySet()
					.stream()
					.filter(Objects::nonNull)
					.map(e -> new Entry<>(unmarshal(e.getKey()), e.getValue()))
					.filter(Entry::nonNull)
					.collect(Collectors.toMap(Entry::getKey, e0 -> //
					// inner map
					e0.getValue()
							.entrySet()
							.stream()
							.filter(Objects::nonNull)
							.map(e -> new Entry<>(unmarshal(e.getKey()), e.getValue()))
							.filter(Entry::nonNull)
							.collect(Collectors.toMap(Entry::getKey, e1 -> //
							// proto map
							e1.getValue()
									.entrySet()
									.stream()
									.filter(Objects::nonNull)
									.map(e -> new Entry<>(Protocol.fromString(e.getKey()), e.getValue()))
									.filter(Entry::nonNull)
									.collect(Collectors.toMap(Entry::getKey, Entry::getValue)))))));
		}

		public static IpAddress<?> unmarshal(String address) {
			if ((address = Util.fixEmptyAndTrim(address)) == null)
				return null;
			final String[] arr = address.split("|");
			if (arr.length != 2)
				return null;
			switch (arr[0]) {
			case "v4":
				return IpUtils.IP4_PARSER.apply(arr[1]);
			case "v6":
				return IpUtils.IP6_PARSER.apply(arr[1]);
			}
			return null;
		}

		public static TrafficMapSnapshot marshal(final TrafficMap map) {
			return new TrafficMapSnapshot(map.forceSameIpType, map.data.entrySet()
					.stream()
					.filter(Objects::nonNull)
					.map(e -> new Entry<>(marshal(e.getKey()), e.getValue()))
					.filter(Entry::nonNull)
					.collect(Collectors.toMap(Entry::getKey, e0 -> //
					// inner map
					e0.getValue()
							.entrySet()
							.stream()
							.filter(Objects::nonNull)
							.map(e -> new Entry<>(marshal(e.getKey()), e.getValue()))
							.filter(Entry::nonNull)
							.collect(Collectors.toMap(Entry::getKey, e1 -> //
							// proto map
							e1.getValue()
									.entrySet()
									.stream()
									.filter(Objects::nonNull)
									.map(e -> new Entry<>(Protocol.toString(e.getKey()), e.getValue()))
									.filter(Entry::nonNull)
									.collect(Collectors.toMap(Entry::getKey, Entry::getValue))))))

			);
		}

		public static String marshal(final IpAddress<?> address) {
			if (address == null)
				return null;
			if (address instanceof Ip4Address)
				return "v4|" + address.toCidrNotation();
			if (address instanceof Ip6Address)
				return "v6|" + address.toCidrNotation();
			return null;
		}

		protected static class Entry<K, V> {

			protected K key;
			protected V value;

			public Entry(final K key, final V value) {
				this.key = key;
				this.value = value;
			}

			public K getKey() {
				return this.key;
			}

			public V getValue() {
				return this.value;
			}

			public K setKey(final K key) {
				final K oldKey = this.key;
				this.key = key;
				return oldKey;
			}

			public V setValue(final V value) {
				final V oldValue = this.value;
				this.value = value;
				return oldValue;
			}

			public boolean nonNullKey() {
				return this.key != null;
			}

			public boolean nonNullValue() {
				return this.value != null;
			}

			public boolean nonNull() {
				return this.key != null && this.value != null;
			}

			public boolean nonNullOrEmptyKey() {
				return !(this.key == null || this.key instanceof Collection<?> col && col.isEmpty());
			}

			public boolean nonNullOrEmptyValue() {
				return !(this.value == null || this.value instanceof Collection<?> col && col.isEmpty());
			}
		}
	}
}
