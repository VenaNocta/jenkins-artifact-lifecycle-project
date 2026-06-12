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

import java.util.UUID;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;

public interface RepoProxyConfigurationProvider extends ExtensionPoint {

	/**
	 * Asks the provider to return the config
	 *
	 * @param run the Run from where the config is requested
	 * @param id  the config-id
	 * @return {@code ProxyConfig} when the provider has a definition covering the
	 *         given id; otherwise {@code null}
	 */
	@CheckForNull
	public ProxyConfig getConfigById(@Nullable final Run<?, ?> run, @NonNull final UUID id);

	/**
	 * Asks the provider to return the config
	 *
	 * @param run  the Run from where the config is requested
	 * @param name the config-name
	 * @return {@code ProxyConfig} when the provider has a definition covering the
	 *         given id; otherwise {@code null}
	 */
	@CheckForNull
	public ProxyConfig getConfigByName(@Nullable final Run<?, ?> run, @NonNull final String name);

	public static ExtensionList<RepoProxyConfigurationProvider> all() {
		return ExtensionList.lookup(RepoProxyConfigurationProvider.class);
	}

	@CheckForNull
	public static ProxyConfig findConfigById(@Nullable final Run<?, ?> run, @NonNull final UUID id) {
		ProxyConfig cnf = null;
		for (RepoProxyConfigurationProvider provider : all()) {
			cnf = provider.getConfigById(run, id);
			if (cnf != null)
				break;
		}
		return cnf;
	}

	@CheckForNull
	public static ProxyConfig findConfigByName(@Nullable final Run<?, ?> run, @NonNull final String name) {
		ProxyConfig cnf = null;
		for (RepoProxyConfigurationProvider provider : all()) {
			cnf = provider.getConfigByName(run, name);
			if (cnf != null)
				break;
		}
		return cnf;
	}
}
