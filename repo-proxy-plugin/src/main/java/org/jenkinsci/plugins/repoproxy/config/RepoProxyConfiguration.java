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
package org.jenkinsci.plugins.repoproxy.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.AGlobalNameResolver;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifiedByUUID;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.artifact.lifecycle.config.NameResolver;
import org.jenkinsci.plugins.artifact.lifecycle.ui.GlobalArtifactLifecycleConfigLink.ArtifactLifecycleCategory;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalRepoProxyConfig")
public class RepoProxyConfiguration extends GlobalConfiguration {

	protected transient final GlobalProxyResolver resolver;
	protected transient final Map<UUID, ProxyConfig> proxyMap = new LinkedHashMap<>();

	private Collection<ProxyConfig> configs = new LinkedList<>();

	public RepoProxyConfiguration() {
		this.resolver = new GlobalProxyResolver(this);
		load();
	}

	@Override
	public synchronized void load() {
		super.load();
		this.proxyMap.clear();
		this.configs.stream()
				.forEachOrdered(t -> this.proxyMap.put(t.getUuid(), t));
	}

	public ProxyConfig getConfigById(final UUID id) {
		return this.proxyMap.get(id);
	}

	public boolean containsConfigId(final UUID uuid) {
		return this.proxyMap.containsKey(uuid);
	}

	// used by jelly & JCasC
	public List<ProxyConfig> getConfigs() {
		return new ArrayList<ProxyConfig>(this.proxyMap.values());
	}

	// used by jelly
	public List<ProxyConfig> getConfigs(final Item context) {
		// TODO actually check the owner Item!
		return new ArrayList<>(this.proxyMap.values());
	}

	@DataBoundSetter
	// used by JCasC
	public void setConfigs(@NonNull final Collection<ProxyConfig> lst) {
		setConfigMap(lst.stream()
				.collect(Collectors.toMap(ProxyConfig::getUuid, Function.identity())));
	}

	public void setConfigMap(final Map<UUID, ProxyConfig> repos) {
		// TODO consider using a read-write-lock
		IdentifierUtils.updateMapEntries(this.proxyMap, repos);
	}

	@Override
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		this.setConfigMap(IdentifierUtils.bindIdentifiedEntries(req, json, ProxyConfig.class, this::containsConfigId,
				"configs", IdentifiedByUUID.JSON_ID_FIELD_NAME, null));
		save();
		return true;
	}

	@Override
	public synchronized void save() {
		this.configs = getConfigs();
		super.save();
	}

	@Override
	public GlobalConfigurationCategory getCategory() {
		return GlobalConfigurationCategory.get(ArtifactLifecycleCategory.class);
	}

	@NonNull
	public static RepoProxyConfiguration get() {
		return GlobalConfiguration.all()
				.getInstance(RepoProxyConfiguration.class);
	}

	public static GlobalProxyResolver getResolver() {
		return get().resolver;
	}

	public static class GlobalProxyResolver extends AGlobalNameResolver<ProxyConfig, NamedProxyConfigRef> {

		private final RepoProxyConfiguration idx;

		protected GlobalProxyResolver(final RepoProxyConfiguration idx) {
			this.idx = idx;
		}

		@Override
		protected ExtensionList<? extends NameResolver<NamedProxyConfigRef>> allResolvers() {
			return ProxyNameResolver.all();
		}

		@Override
		public ProxyConfig getEntryById(final UUID uuid) {
			return this.idx.getConfigById(uuid);
		}

		@Override
		public Stream<ProxyConfig> getEntriesAsStream(final Item context) {
			// TODO actually check the owner Item!
			return this.idx.proxyMap.values()
					.parallelStream()
					.filter(Objects::nonNull);
		}
	}
}
