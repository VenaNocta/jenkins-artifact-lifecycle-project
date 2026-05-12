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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.util.FormValidation;
import net.runeduniverse.lib.repo.maven.client.http.HttpSource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;

public class HttpRepoSourceConfig extends RepoSourceConfig {

	private static final long serialVersionUID = 1L;

	protected int maxRedirects;
	protected int maxRetries;

	@DataBoundConstructor
	public HttpRepoSourceConfig(final String key) {
		super(key);
	}

	public int getMaxRedirects() {
		return this.maxRedirects;
	}

	public int getMaxRetries() {
		return this.maxRetries;
	}

	@DataBoundSetter
	public void setMaxRedirects(final int maxRedirects) {
		this.maxRedirects = maxRedirects;
	}

	@DataBoundSetter
	public void setMaxRetries(final int maxRetries) {
		this.maxRetries = maxRetries;
	}

	@Override
	public RepositorySource buildSource(final Path repoPath) {
		return new HttpSource(this.key, this.uri, repoPath.resolve(this.key), this.maxRedirects, this.maxRetries);
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

		public int defaultMaxRedirects() {
			return 3;
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckMaxRedirects(@QueryParameter final String value) {
			try {
				int v = Integer.parseInt(value);
				if (v < 0) {
					return FormValidation.error("");
				}
				return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("");
			}
		}

		public int defaultMaxRetries() {
			return 10;
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckMaxRetries(@QueryParameter final String value) {
			try {
				int v = Integer.parseInt(value);
				if (v < 0) {
					return FormValidation.error("");
				}
				return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("");
			}
		}
	}
}
