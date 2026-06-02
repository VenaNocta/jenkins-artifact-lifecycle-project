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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.runeduniverse.lib.utils.net.IpNetworkUtils;
import net.runeduniverse.lib.utils.net.api.CidrObject;
import net.runeduniverse.lib.utils.net.api.IpAddress;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;
import net.runeduniverse.lib.utils.net.api.IpNetAddress;

public class IpNetUtils {

	public static final Function<String, IpNet4Address> IPNET4_PARSER = IpNetworkUtils::tryParseIpNet4Address;
	public static final Function<String, IpNet6Address> IPNET6_PARSER = IpNetworkUtils::tryParseIpNet6Address;

	private IpNetUtils() {
	}

	public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>> List<String> marshalCol(
			final Collection<N> col) {
		if (col == null || col.isEmpty())
			return Collections.emptyList();

		return col.stream()
				.map(CidrObject::toCidrNotation)
				.toList();
	}

	public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>, C extends Collection<N>> C unmarshalCol(
			final Supplier<C> supplier, final Collection<String> data, final Function<String, N> parser) {

		if (data == null || data.isEmpty())
			return supplier.get();

		return data.stream()
				.map(parser)
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(supplier));
	}

	public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>, X> Map<String, X> marshalMapKeys(
			final Map<N, X> map) {

		if (map == null || map.isEmpty())
			return Collections.emptyMap();

		return map.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> e.getKey()
						.toCidrNotation(), Entry::getValue));
	}

	public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>, M extends Map<N, X>, X> M unmarshalMapKeys(
			final Supplier<M> supplier, final Map<String, X> data, final Function<String, N> parser) {

		if (data == null || data.isEmpty())
			return supplier.get();

		return data.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> parser.apply(e.getKey()), Entry::getValue, (a, b) -> b, supplier));
	}

	public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>, X> Map<X, String> marshalMapValues(
			final Map<X, N> map) {

		if (map == null || map.isEmpty())
			return Collections.emptyMap();

		return map.entrySet()
				.stream()
				.collect(Collectors.toMap(Entry::getKey, e -> e.getValue()
						.toCidrNotation()));
	}

	public static <A extends IpAddress<A>, N extends IpNetAddress<A, N>, M extends Map<X, N>, X> M unmarshalMapValues(
			final Supplier<M> supplier, final Map<X, String> data, final Function<String, N> parser) {

		if (data == null || data.isEmpty())
			return supplier.get();

		return data.entrySet()
				.stream()
				.collect(Collectors.toMap(Entry::getKey, e -> parser.apply(e.getValue()), (a, b) -> b, supplier));
	}
}
