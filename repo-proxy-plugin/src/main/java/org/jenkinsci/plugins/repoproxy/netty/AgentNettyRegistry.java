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
package org.jenkinsci.plugins.repoproxy.netty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import hudson.remoting.VirtualChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;

public class AgentNettyRegistry {

	protected static final Map<UUID, ServerHandle> SERVERS = new ConcurrentHashMap<>();

	public static synchronized ServerHandle register(final VirtualChannel ch, final String flowNodeId,
			final ChannelGroup group) {
		UUID id;
		do {
			id = UUID.randomUUID();
		} while (SERVERS.containsKey(id));

		final ServerHandle handle = new ServerHandle(id, ch, flowNodeId, group);
		SERVERS.put(id, handle);
		return handle;
	}

	public static ServerHandle get(final UUID uuid) {
		return SERVERS.get(uuid);
	}

	public static void stopServers(final Collection<UUID> uuids) throws InterruptedException {
		final List<ChannelGroupFuture> futures = new ArrayList<>();
		// signal all servers to shutdown
		for (UUID uuid : uuids) {
			final ServerHandle handle = SERVERS.remove(uuid);
			if (handle == null)
				continue;
			futures.add(handle.getChannelGroup()
					.close());
		}
		// wait for them to do so
		for (ChannelGroupFuture future : futures)
			future.await(60, TimeUnit.MINUTES);
	}

	public static Stream<ServerHandle> stream() {
		return SERVERS.values()
				.stream();
	}

	public static class ServerHandle {

		protected final UUID id;
		protected final VirtualChannel jenkinsChannel;
		protected final String flowNodeId;
		protected final ChannelGroup channelGroup;

		protected ServerHandle(final UUID id, final VirtualChannel ch, final String flowNodeId,
				final ChannelGroup group) {
			this.id = id;
			this.jenkinsChannel = ch;
			this.flowNodeId = flowNodeId;
			this.channelGroup = group;
		}

		public UUID getId() {
			return this.id;
		}

		public VirtualChannel getJenkinsChannel() {
			return this.jenkinsChannel;
		}

		public String getFlowNodeId() {
			return this.flowNodeId;
		}

		public ChannelGroup getChannelGroup() {
			return this.channelGroup;
		}
	}
}
