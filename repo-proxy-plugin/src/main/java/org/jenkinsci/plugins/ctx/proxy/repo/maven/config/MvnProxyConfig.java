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
package org.jenkinsci.plugins.ctx.proxy.repo.maven.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ctx.proxy.repo.config.ProxyConfig;
import org.jenkinsci.plugins.ctx.proxy.repo.config.ProxyConfigDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

public class MvnProxyConfig extends ProxyConfig {

	private static final long serialVersionUID = 1L;

	protected Map<String, RepoSourceConfig> sourceMap = new LinkedHashMap<>();

	public MvnProxyConfig(@NonNull final String id) {
		super(id);
	}

	@DataBoundConstructor
	public MvnProxyConfig(@NonNull final String id, @CheckForNull final String description) {
		super(id, description);
	}

	public List<RepoSourceConfig> getSources() {
		return new ArrayList<RepoSourceConfig>(this.sourceMap.values());
	}

	@DataBoundSetter
	public void setSources(@NonNull final List<RepoSourceConfig> sources) {
		// ignore unchanged
		this.sourceMap.values()
				.retainAll(sources);
		sources.removeAll(this.sourceMap.values());

		// process additions
		for (RepoSourceConfig source : sources) {
			if (source == null)
				continue;
			// check repo id for null or duplicates
			String key = StringUtils.trimToNull(source.getKey());
			if (key == null)
				continue;
			while (this.sourceMap.containsKey(key)) {
				// if id is null, skip repo
				source.setKey(key = UUID.randomUUID()
						.toString());
			}
			this.sourceMap.put(key, source);
		}
	}

	@Extension
	public static class Descriptor extends ProxyConfigDescriptor {

		@Override
		public String getDisplayName() {
			return "Maven Repository";
		}
	}
}
