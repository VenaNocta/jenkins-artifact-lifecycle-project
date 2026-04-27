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
package org.jenkinsci.plugins.airgap;

import java.io.IOException;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnixDomainSocketAddress;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.LauncherDecorator;
import hudson.FilePath.FileCallable;
import hudson.remoting.Future;

public interface IsolationContext extends Serializable {

	public LauncherDecorator launcherDecorator();

	public void setup();

	public void destroy();

	public <T> T actInContext(FileCallable<T> callable) throws IOException, InterruptedException;

	public <T> Future<T> actInContextAsync(FileCallable<T> callable) throws IOException, InterruptedException;

	public UnixDomainSocketAddress connectUnixSocket(String path);

	public InetLink connectInetLink();

	public static interface InetLink extends Serializable {

		public int getNetworkPrefixV4();

		public int getNetworkPrefixV6();

		@CheckForNull
		public Inet4Address getHostAddressV4();

		@CheckForNull
		public Inet6Address getHostAddressV6();

		@CheckForNull
		public Inet4Address getContextAddressV4();

		@CheckForNull
		public Inet6Address getContextAddressV6();

		public default boolean hasIpv4Support() {
			return getNetworkPrefixV4() != -1;
		}

		public default boolean hasIpv6Support() {
			return getNetworkPrefixV6() != -1;
		}
	}
}
