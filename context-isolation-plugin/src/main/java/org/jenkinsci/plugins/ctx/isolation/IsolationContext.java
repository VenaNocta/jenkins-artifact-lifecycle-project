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
package org.jenkinsci.plugins.ctx.isolation;

import java.io.IOException;
import java.io.Serializable;
import java.net.UnixDomainSocketAddress;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.LauncherDecorator;
import hudson.FilePath.FileCallable;
import hudson.remoting.Future;
import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;

public interface IsolationContext extends Serializable {

	public LauncherDecorator launcherDecorator();

	public void setup();

	public void destroy();

	public <T> T actInContext(FileCallable<T> callable) throws IOException, InterruptedException;

	public <T> Future<T> actInContextAsync(FileCallable<T> callable) throws IOException, InterruptedException;

	public UnixDomainSocketAddress connectUnixSocket(String path);

	public NetLink connectNetLink();

	public void disconnectNetLink(NetLink link);

	public static interface NetLink extends Serializable {

		public short getNetworkPrefixV4();

		public short getNetworkPrefixV6();

		@CheckForNull
		public IpNet4Address getSubnetV4();

		@CheckForNull
		public IpNet6Address getSubnetV6();

		@CheckForNull
		public Ip4Address getHostAddressV4();

		@CheckForNull
		public Ip6Address getHostAddressV6();

		@CheckForNull
		public Ip4Address getContextAddressV4();

		@CheckForNull
		public Ip6Address getContextAddressV6();

		public default boolean hasIpv4Support() {
			return getNetworkPrefixV4() != -1;
		}

		public default boolean hasIpv6Support() {
			return getNetworkPrefixV6() != -1;
		}
	}
}
