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
package org.jenkinsci.plugins.isolationctx.handler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import net.runeduniverse.lib.utils.net.api.IpAddress;

public interface NodeIsolationHandler {

	public void firewallSetup() throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputOpen(A host, A external, Set<Integer> ports)
			throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputOpen(Map<A, Map<A, Set<Integer>>> data)
			throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputRetainOpen(Map<A, Map<A, Set<Integer>>> data)
			throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputCloseAll() throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputCloseAll(A host) throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputCloseAll(A host, A external)
			throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputClose(A host, A external, Set<Integer> ports)
			throws IOException, InterruptedException;

	public <A extends IpAddress<A>> void firewallInputClose(Map<A, Map<A, Set<Integer>>> data)
			throws IOException, InterruptedException;

}
