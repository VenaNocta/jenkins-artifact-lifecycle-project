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
package org.jenkinsci.plugins.repoproxy.maven.config;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.GlobalRepositoryIndex;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.artifact.lifecycle.maven.config.Maven2Repository;
import org.jenkinsci.plugins.repoproxy.config.BomSource;
import org.jenkinsci.plugins.repoproxy.config.BomSourceProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import net.runeduniverse.lib.repo.maven.client.http.HttpSource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.utils.common.URIBuilder;

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

	@Override
	public Set<String> getCredentialIds() {
		final Maven2Repository repo = getRepo();
		final String credId;
		if (repo == null || (credId = repo.getCredentialId()) == null)
			return super.getCredentialIds();
		return Set.of(credId);
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
	public RepositorySource buildSource(final Path repoPath, final Map<UUID, Maven2Repository> repoMap,
			final Map<String, IdCredentials> credentialMap) {
		final Maven2Repository repo = repoMap.get(this.repoId);
		if (!Descriptor.isSupported(repo))
			return null;
		final HttpSource source = new HttpSource(this.key, fixRepoUri(repo.getMaven2Uri()), repoPath.resolve(this.key),
				this.maxRedirects, this.maxRetries);
		// set repo credentials
		if (credentialMap.get(repo.getCredentialId()) instanceof StandardUsernamePasswordCredentials cred) {
			source.setRepoCredentials(new DefaultBasicRepoCredentials(cred));
		}
		return source;
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

		public ListBoxModel doFillRepoIdItems(@AncestorInPath final Item item, @QueryParameter final String repoId) {
			return IdentifierUtils.allValuesAsListBox(GlobalRepositoryIndex.get()
					.getRepos(Descriptor::isSupported, Set.of(Maven2Repository.class)), repoId,
					ArtifactRepository::getName, false);
		}

		public static boolean isSupported(final ArtifactRepository repository) {
			if (repository instanceof Maven2Repository repo)
				return repo.hasMaven2Support() && new URIBuilder(repo.getMaven2Uri()).hasScheme(Descriptor.PROTOCOLS);
			return false;
		}
	}
}
