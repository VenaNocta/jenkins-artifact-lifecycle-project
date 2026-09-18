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

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepositoryTags;
import org.jenkinsci.plugins.artifact.lifecycle.config.GlobalRepositoryIndex;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import net.runeduniverse.lib.repo.maven.client.http.HttpSource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;

public class HttpRepoSourceConfig extends RepoSourceConfig {

	private static final long serialVersionUID = 1L;

	protected int maxRedirects;
	protected int maxRetries;
	@CheckForNull
	protected String credentialsId;
	protected UUID repoId;

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
	@CheckForNull
	public UUID getRepoId() {
		return this.repoId;
	}

	@CheckForNull
	public String getCredentialsId() {
		return this.credentialsId;
	}

	@Override
	public Set<String> getCredentialIds() {
		if (this.credentialsId == null)
			return super.getCredentialIds();
		return Set.of(getCredentialsId());
	}

	@DataBoundSetter
	public void setMaxRedirects(final int maxRedirects) {
		this.maxRedirects = maxRedirects;
	}

	@DataBoundSetter
	public void setMaxRetries(final int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public void setRepoIdValue(final UUID value) {
		this.repoId = value;
	}

	@DataBoundSetter
	public void setRepoId(String value) {
		try {
			value = Util.fixEmptyAndTrim(value);
			this.repoId = value == null ? null : UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			this.repoId = null;
		}
	}

	@DataBoundSetter
	public void setCredentialsId(final String credentialsId) {
		this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
	}

	@Override
	public RepositorySource buildSource(final Path repoPath, final Map<String, IdCredentials> credentialMap) {
		final HttpSource source = new HttpSource(this.key, this.uri, repoPath.resolve(this.key), this.maxRedirects,
				this.maxRetries);
		// set repo credentials
		if (credentialMap.get(this.credentialsId) instanceof StandardUsernamePasswordCredentials cred) {
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

		public ListBoxModel allValuesAsListBox(final Item item) {
			return GlobalRepositoryIndex.allValuesAsListBox(GlobalRepositoryIndex.get()
					.getRepos(/*
								 * repo -> repo.matchTags(ArtifactRepositoryTags.TYPE__SOURCE::equals) &&
								 * repo.matchTags(ArtifactRepositoryTags.PROTOCOL__MAVEN2_HTTP::equals)
								 */));
		}

		public ListBoxModel doFillRepoIdItems(@AncestorInPath final Item item, @QueryParameter final String repoId) {
			final ListBoxModel model = allValuesAsListBox(item);

			boolean hasValue = false;
			for (Option option : model) {
				if (repoId != null && repoId.equals(option.value))
					hasValue = true;
				final String name = Util.fixEmptyAndTrim(option.name);
				option.name = String.format("%s » %s", option.value, name == null ? "???" : name);
			}
			if (!hasValue) {
				if (Util.fixEmptyAndTrim(repoId) == null || "---".equals(repoId))
					model.add(0, new Option("- none -", repoId));
				else
					model.add(0, new Option(String.format("%s » ???", repoId), repoId));
			}
			return model;
		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item) {
			if (item == null && !Jenkins.get()
					.hasPermission(Jenkins.ADMINISTER) || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
				return new StandardListBoxModel();
			}
			return new StandardListBoxModel().includeEmptyValue()
					.includeAs(ACL.SYSTEM2, item, StandardUsernamePasswordCredentials.class);
		}
	}
}
