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

import java.util.Collection;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class HttpRepoSourceConfig extends RepoSourceConfig {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public HttpRepoSourceConfig(final String key) {
		super(key);
	}

	@Extension
	public static class Descriptor extends RepoSourceConfigDescriptor {

		public static final String DEFAULT_PROTOCOL = "https";
		public static final List<String> PROTOCOLS = List.of("http", "https");

		@Override
		public String getDisplayName() {
			return "Http Repo Source";
		}

		@Override
		public String defaultProtocol() {
			return DEFAULT_PROTOCOL;
		}

		@Override
		public Collection<String> protocols() {
			return PROTOCOLS;
		}
	}
}
