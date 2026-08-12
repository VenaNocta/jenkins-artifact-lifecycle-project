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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Map.Entry;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
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

	// used by jelly
	public List<ProxyConfig> getConfigs() {
		return new ArrayList<ProxyConfig>(this.proxyMap.values());
	}

	@DataBoundSetter
	public void setConfigs(@NonNull final Collection<ProxyConfig> lst) {
		setConfigMap(lst.stream()
				.collect(Collectors.toMap(ProxyConfig::getId, Function.identity())));
	}

	public void setConfigMap(final Map<UUID, ProxyConfig> repos) {
		// TODO consider using a read-write-lock
		// retain & update old "known" entries
		for (Iterator<Entry<UUID, ProxyConfig>> i = this.proxyMap.entrySet()
				.iterator(); i.hasNext();) {
			final Entry<UUID, ProxyConfig> entry = i.next();
			final UUID id = entry.getKey();
			final ProxyConfig newProxyConfig = repos.remove(id);
			if (newProxyConfig == null) {
				// value got deleted
				i.remove();
				continue;
			}
			entry.setValue(newProxyConfig);
		}
		this.proxyMap.putAll(repos);
	}

	@Override
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		JSONArray configArray = json.optJSONArray("configs");
		if (configArray == null) {
			configArray = new JSONArray();
			JSONObject jsonObj = json.optJSONObject("configs");
			if (jsonObj != null)
				configArray.add(jsonObj);
		}

		final Map<UUID, ProxyConfig> newProxyMap = new LinkedHashMap<>();

		if (configArray != null) {
			for (int i = 0; i < configArray.size(); i++) {
				final JSONObject configObj = configArray.getJSONObject(i);
				final String parsedId = Util.fixEmpty(configObj.optString(ProxyConfig.JSON_ID_FIELD_NAME));
				UUID id = null;
				if (parsedId != null) {
					// try parse, invalid equals null value
					try {
						id = UUID.fromString(parsedId);
					} catch (IllegalArgumentException ignored) {
					}
				}
				if (id == null) {
					// generate unique entry.id in context
					do {
						id = UUID.randomUUID();
					} while (this.proxyMap.containsKey(id) || newProxyMap.containsKey(id));
					configObj.element(ProxyConfig.JSON_ID_FIELD_NAME, id.toString());
				} else {
					// check for duplicate ids
					if (newProxyMap.containsKey(id))
						continue;
				}

				final ProxyConfig config = req.bindJSON(ProxyConfig.class, configObj);
				if (!id.equals(config.id)) {
					throw new FormException("UUID mismatch after binding", "configs");
				}
				newProxyMap.put(config.id, config);
			}
		}
		this.setConfigMap(newProxyMap);
		save();
		return true;
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
