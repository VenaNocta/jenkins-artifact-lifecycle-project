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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jenkinsci.plugins.repoproxy.config.SBomSourceCache;
import org.jenkinsci.plugins.repoproxy.maven.config.MvnProxyConfig;
import org.jenkinsci.plugins.repoproxy.netty.AgentNettyRegistry;
import org.jenkinsci.plugins.repoproxy.netty.AgentNettyRegistry.ServerHandle;
import org.jenkinsci.plugins.repoproxy.netty.AgentNettyStartCallable;
import org.jenkinsci.plugins.repoproxy.steps.ArtifactValidationInterruption;
import org.jenkinsci.plugins.repoproxy.steps.StopStepCallable;

import com.cloudbees.plugins.credentials.common.IdCredentials;

import hudson.remoting.VirtualChannel;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import io.netty.channel.group.ChannelGroup;
import net.runeduniverse.lib.repo.maven.data.DefaultFileTypeIndex;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.DefaultCache;
import net.runeduniverse.lib.repo.maven.proxy.DefaultRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;

public class MvnNettyStartCallable extends AgentNettyStartCallable {

	private static final long serialVersionUID = 1L;

	protected final Map<String, MvnProxyConfig> instanceConfigs;
	protected final Map<UUID, SBomSourceCache> sbomCacheMap;
	protected final Map<String, IdCredentials> credentialMap;
	protected final Set<InetAddress> addressSet;
	protected final int port;

	public MvnNettyStartCallable(final String flowNodeId, final Map<String, MvnProxyConfig> instanceConfigs,
			final Map<UUID, SBomSourceCache> sbomCacheMap, final Map<String, IdCredentials> credentialMap,
			final Set<InetAddress> addressSet, final int port) {
		super(flowNodeId);
		this.instanceConfigs = instanceConfigs;
		this.sbomCacheMap = sbomCacheMap;
		this.credentialMap = credentialMap;
		this.addressSet = addressSet;
		this.port = port;
	}

	@Override
	protected void attachServerChannel(final UUID handleId, final File cacheFolder, final ChannelGroup channelGroup)
			throws IOException, InterruptedException {
		final ProxyServer server = new ProxyServer(new DefaultFileTypeIndex(), HttpRepoServerInitializer::new);
		final Path cachePath = cacheFolder.toPath();

		configure(server, cachePath, e -> {
			final ServerHandle handle = AgentNettyRegistry.get(handleId);
			killPipeline(handle == null ? null : handle.getJenkinsChannel(), e);
		});

		for (InetAddress address : this.addressSet) {
			channelGroup.add(server.bindChannel(address, this.port)
					.sync()
					.channel());
		}
		channelGroup.newCloseFuture()
				.addListener(v -> {
					server.shutdownGracefully();
				});
	}

	public boolean killPipeline(final VirtualChannel channel, final ArtifactException ex) {
		try {
			if (channel != null)
				channel.callAsync(new StopStepCallable(this.flowNodeId,
						new ArtifactValidationInterruption(ex.getMessage()).setPURL(ex.getPURL())));
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	protected void configure(final ProxyServer server, final Path cachePath,
			final Consumer<ArtifactException> pipelineKiller) throws IOException, InterruptedException {
		final Map<UUID, Path> sbomCacheMap = resolveSBomCacheMap();
		for (Entry<String, MvnProxyConfig> entry : this.instanceConfigs.entrySet()) {
			final MvnProxyConfig config = entry.getValue();

			final DefaultRepositoryProxyInstance instance = new DefaultRepositoryProxyInstance(entry.getKey(),
					DefaultCache::new);

			final Path repoPath = cachePath.resolve(entry.getKey());
			instance.sources()
					.putAll(config.buildSourceMap(repoPath, sbomCacheMap, this.credentialMap, pipelineKiller));

			server.addInstance(instance);
		}
	}

	protected Map<UUID, Path> resolveSBomCacheMap() throws IOException, InterruptedException {
		final Map<UUID, Path> map = new LinkedHashMap<>();
		for (Entry<UUID, SBomSourceCache> entry : this.sbomCacheMap.entrySet()) {
			final UUID uuid = entry.getKey();
			final SBomSourceCache cache = entry.getValue();
			if (uuid == null || cache == null)
				continue;
			map.put(uuid, cache.toPath());
		}
		return map;
	}
}
