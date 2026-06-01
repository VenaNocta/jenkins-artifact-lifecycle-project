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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import jenkins.model.GlobalConfiguration;

@Extension
@Symbol("globalRepoProxyConfig")
public class RepoProxyConfiguration extends GlobalConfiguration {

	protected Map<String, ProxyConfig> repoMap = new LinkedHashMap<>();

	public RepoProxyConfiguration() {
		load();
	}

	public ProxyConfig getConfig(final String id) {
		return this.repoMap.get(id);
	}

	public List<ProxyConfig> getConfigs() {
		return new ArrayList<ProxyConfig>(this.repoMap.values());
	}

	@DataBoundSetter
	public void setConfigs(@NonNull final List<ProxyConfig> configs) {
		// ignore unchanged
		this.repoMap.values()
				.retainAll(configs);
		configs.removeAll(this.repoMap.values());

		// process additions
		OUTER: for (ProxyConfig repo : configs) {
			if (repo == null)
				continue;
			final ProxyConfigDescriptor repoDesc = repo.getDescriptor();
			// check repo id for null or duplicates
			String id = StringUtils.trimToNull(repo.getId());
			while (id == null || this.repoMap.containsKey(id)) {
				id = StringUtils.trimToNull(repoDesc.randomId());
				// if id is null, skip repo
				if (id == null)
					continue OUTER;
				repo.setId(id = repoDesc.randomId());
			}
			this.repoMap.put(id, repo);
		}
		save();
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
		public Optional<ProxyConfig> getConfig(final Run<?, ?> run, final String id) {
			return Optional.ofNullable(this.cnf.getConfig(id));
		}
	}
}
