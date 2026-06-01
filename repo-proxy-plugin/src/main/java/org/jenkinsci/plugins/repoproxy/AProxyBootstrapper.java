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
package org.jenkinsci.plugins.repoproxy;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public abstract class AProxyBootstrapper implements ProxyBootstrapper {

	public static Map<Short, Set<IpAddress<?>>> groupByIpFamily(final Collection<IpAddress<?>> allIps) {
		final Map<Short, Set<IpAddress<?>>> map = new TreeMap<>();
		for (IpAddress<?> ip : allIps) {
			map.computeIfAbsent(ip.getIpFamily(), k -> new HashSet<>())
					.add(ip);
		}
		return map;
	}

	public static void updateEnv(final Set<String> refIds, final Set<IpAddress<?>> ipSet, final int port,
			final Map<String, String> proxyEnv, final String envPrefix) {
		final Map<Short, Set<IpAddress<?>>> groupedIps = groupByIpFamily(ipSet);
		if (ipSet.isEmpty())
			return;

		for (String refId : refIds) {
			final String defaultEnvId = envPrefix + "_PROXY_" + refId + "_URL";
			// only 1 ip -> default-entry
			if (ipSet.size() == 1) {
				proxyEnv.put(defaultEnvId, String.format("http://%s:%d/%s/", ipSet.iterator()
						.next()
						.toCidrNotation(), port, refId));
				continue;
			}
			// more then 1 ip
			// -> default-entry
			// + an entry for each value postfixed with ip family and counter
			String envId0 = null;
			for (Entry<Short, Set<IpAddress<?>>> entry : groupedIps.entrySet()) {
				final short ipFamily = entry.getKey();
				int cnt = 0;
				for (IpAddress<?> ip : ipSet) {
					String envId = defaultEnvId + "_ip" + ipFamily;
					if (0 < cnt)
						envId = envId + "_" + cnt;
					if (envId0 == null)
						envId0 = envId;

					proxyEnv.put(envId, String.format("http://%s:%d/%s/", ip.toCidrNotation(), port, refId));
				}
			}
			proxyEnv.put(defaultEnvId, proxyEnv.get(envId0));
		}
	}
}
