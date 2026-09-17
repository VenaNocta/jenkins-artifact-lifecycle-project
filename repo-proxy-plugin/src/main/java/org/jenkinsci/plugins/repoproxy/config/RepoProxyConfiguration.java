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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalRepoProxyConfig")
public class RepoProxyConfiguration extends GlobalConfiguration {

	protected Map<UUID, ProxyConfig> proxyMap = new LinkedHashMap<>();

	public RepoProxyConfiguration() {
		load();
	}

	public ProxyConfig getConfigById(final UUID id) {
		return this.proxyMap.get(id);
	}

	public ProxyConfig getConfigByName(final String name) {
		if (name == null)
			return null;
		return this.proxyMap.values()
				.stream()
				.filter(s -> name.equals(s.getName()))
				.findFirst()
				.orElse(null);
	}

	// used by jelly & JCasC
	public List<ProxyConfig> getConfigs() {
		return new ArrayList<ProxyConfig>(this.proxyMap.values());
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
		this.proxyMap.putAll(repos);
	}

	@Override
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		final JSONArray configArray = IdentifierUtils.getOptJsonAsArray(json, "configs");
		this.setConfigMap(IdentifierUtils.bindIdentifiedEntries(req, configArray, ProxyConfig.class,
				this.proxyMap::containsKey, this::verifyPostBind, ProxyConfig.JSON_ID_FIELD_NAME));
		save();
		return true;
	}

	protected FormException verifyPostBind(final UUID uuid, final ProxyConfig config) {
		if (!uuid.equals(config.uuid))
			return new FormException("UUID mismatch after binding", "configs");
		return null;
	}

	@NonNull
	public static RepoProxyConfiguration get() {
		return GlobalConfiguration.all()
				.getInstance(RepoProxyConfiguration.class);
	}

	// provides the global fallback values
	@Extension(ordinal = -10000)
	public static final class GlobalRepoProxyConfigurationProvider implements RepoProxyConfigurationProvider {

		@Inject
		private RepoProxyConfiguration cnf;

		@Override
		public ProxyConfig getConfigById(final Run<?, ?> run, final UUID id) {
			return this.cnf.getConfigById(id);
		}

		@Override
		public ProxyConfig getConfigByName(final Run<?, ?> run, final String name) {
			return this.cnf.getConfigByName(name);
		}
	}
}
