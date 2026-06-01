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

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.jenkinsci.plugins.repoproxy.netty.AgentNettyRegistry.ServerHandle;

import hudson.remoting.VirtualChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import jenkins.agents.ControllerToAgentFileCallable;

public abstract class AgentNettyStartCallable implements ControllerToAgentFileCallable<UUID> {

	private static final long serialVersionUID = 1L;

	protected final String flowNodeId;

	public AgentNettyStartCallable(final String flowNodeId) {
		this.flowNodeId = flowNodeId;
	}

	protected ChannelGroup newChannelGroup() {
		return new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	}

	protected abstract void attachServerChannel(UUID handleId, File cacheFolder, ChannelGroup channelGroup)
			throws IOException, InterruptedException;

	@Override
	public UUID invoke(final File cacheFolder, final VirtualChannel channel) throws IOException, InterruptedException {
		final ChannelGroup group = newChannelGroup();
		if (group == null)
			return null;
		final ServerHandle handle = AgentNettyRegistry.register(channel, this.flowNodeId, group);
		attachServerChannel(handle.getId(), cacheFolder, group);
		return handle.getId();
	}
}
