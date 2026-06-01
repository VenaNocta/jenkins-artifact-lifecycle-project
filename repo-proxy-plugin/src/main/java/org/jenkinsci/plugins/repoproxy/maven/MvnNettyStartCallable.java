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
package org.jenkinsci.plugins.repoproxy.maven;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;

import org.jenkinsci.plugins.repoproxy.maven.config.MvnProxyConfig;
import org.jenkinsci.plugins.repoproxy.netty.AgentNettyRegistry;
import org.jenkinsci.plugins.repoproxy.netty.AgentNettyStartCallable;
import org.jenkinsci.plugins.repoproxy.steps.ArtifactValidationInterruption;
import org.jenkinsci.plugins.repoproxy.steps.StopStepCallable;

import java.util.Set;
import java.util.UUID;

import io.netty.channel.group.ChannelGroup;
import net.runeduniverse.lib.repo.maven.data.DefaultFileTypeIndex;
import net.runeduniverse.lib.repo.maven.proxy.DefaultCache;
import net.runeduniverse.lib.repo.maven.proxy.DefaultRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;

public class MvnNettyStartCallable extends AgentNettyStartCallable {

	private static final long serialVersionUID = 1L;

	protected final Map<String, MvnProxyConfig> instanceConfigs;
	protected final Set<InetAddress> addressSet;
	protected final int port;

	public MvnNettyStartCallable(final String flowNodeId, Map<String, MvnProxyConfig> instanceConfigs,
			final Set<InetAddress> addressSet, final int port) {
		super(flowNodeId);
		this.instanceConfigs = instanceConfigs;
		this.addressSet = addressSet;
		this.port = port;
	}

	@Override
	protected void attachServerChannel(final UUID handleId, final File cacheFolder, final ChannelGroup channelGroup)
			throws IOException, InterruptedException {
		final ProxyServer server = new ProxyServer(new DefaultFileTypeIndex(), HttpRepoServerInitializer::new);
		final Path cachePath = cacheFolder.toPath();

		configure(server, cachePath);

		for (InetAddress address : this.addressSet) {
			channelGroup.add(server.bindChannel(address, this.port)
					.sync()
					.channel());
		}

		// ---

		if (false) {
			// TODO use this to kill step in case of issues
			AgentNettyRegistry.get(handleId)
					.getJenkinsChannel()
					.callAsync(new StopStepCallable(this.flowNodeId,
							new ArtifactValidationInterruption("SBOM: invalid artifact requested!")));
		}

	}

	protected void configure(final ProxyServer server, final Path cachePath) {
		for (Entry<String, MvnProxyConfig> entry : this.instanceConfigs.entrySet()) {
			// TODO configure with instanceConfigs
			final MvnProxyConfig config = entry.getValue();

			final DefaultRepositoryProxyInstance instance = new DefaultRepositoryProxyInstance(entry.getKey(),
					DefaultCache::new);

			final Path repoPath = cachePath.resolve(entry.getKey());
			instance.sources()
					.putAll(config.buildSourceMap(repoPath));

			server.addInstance(instance);
		}
	}
}
