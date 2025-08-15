/*
 * Copyright © 2025 VenaNocta (venanocta@gmail.com)
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
package org.jenkinsci.plugins.artifact.deploy.repos;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.deploy.config.AbstractArtifactDeploymentRepository;
import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeploymentRepositoryDescriptor;
import org.jenkinsci.plugins.artifact.deploy.config.URIBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import com.cloudbees.plugins.credentials.ContextInPath;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;;

public class SonatypeNexusDeploymentRepository extends AbstractArtifactDeploymentRepository {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_PROTOCOL = "https";
	public static final List<String> PROTOCOLS = List.of("http", "https");

	@CheckForNull
	private URL url;
	@CheckForNull
	private String repo;
	@CheckForNull
	private String credentialsId;

	public SonatypeNexusDeploymentRepository(@NonNull String id, @CheckForNull String description, //
			@CheckForNull URL url, @CheckForNull String repo, @CheckForNull String credentialsId) {
		super(id, description);
		this.url = url;
		this.repo = repo;
		this.credentialsId = credentialsId;
	}

	@DataBoundConstructor
	public SonatypeNexusDeploymentRepository(@NonNull String id, @CheckForNull String description, //
			@CheckForNull String url, @CheckForNull String repo, @CheckForNull String credentialsId) {
		super(id, description);
		setUrl(url);
		this.repo = repo;
		this.credentialsId = credentialsId;
	}

	public URL getUrl() {
		return this.url;
	}

	public String getRepo() {
		return this.repo;
	}

	public String getCredentialsId() {
		return Util.fixEmpty(this.credentialsId);
	}

	@DataBoundSetter
	public void setUrl(@NonNull String url) {
		final URIBuilder builder;
		try {
			builder = URIBuilder.fromString(url);
		} catch (URISyntaxException e) {
			this.url = null;
			return;
		}
		builder.fixScheme(DEFAULT_PROTOCOL);
		this.url = builder.hasScheme(PROTOCOLS) ? builder.toURL(null) : null;
	}

	@DataBoundSetter
	public void setRepo(@NonNull final String repo) {
		this.repo = repo;
	}

	@DataBoundSetter
	public void setCredentialsId(@CheckForNull final String credentialsId) {
		this.credentialsId = credentialsId;
	}

	@Extension
	public static class SonatypeNexusDescriptor extends ArtifactDeploymentRepositoryDescriptor {

		@Override
		public String getDisplayName() {
			return "Sonatype Nexus Repository";
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckUrl(@ContextInPath ModelObject context, @QueryParameter String value) {
			value = StringUtils.trimToNull(value);
			if (value == null)
				return FormValidation.error("URL is required");
			value = StringUtils.stripStart(value, "://");
			final URI uri;
			try {
				uri = new URI(value);
			} catch (URISyntaxException e) {
				return FormValidation.error("Invalid URL");
			}
			final String scheme = uri.getScheme();
			if (scheme == null)
				return FormValidation.warning("Protocol must be 'https' (default) or 'http'");
			if (!List.of("http", "https")
					.contains(scheme))
				return FormValidation.error("Protocol unsupported; allowed Protocols: 'https' (default) or 'http'");
			return FormValidation.ok();
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckRepo(@ContextInPath ModelObject context, @QueryParameter String value) {
			value = StringUtils.trimToNull(value);
			if (value == null)
				return FormValidation.error("Repository is required");
			return FormValidation.ok();
		}

		public ListBoxModel doFillRepoItems(@AncestorInPath Item item, @QueryParameter String url,
				@QueryParameter String repo, @QueryParameter String credentialsId) {
			repo = StringUtils.trimToNull(repo);

			final ListBoxModel dropdown = new ListBoxModel();
			dropdown.add("- none -", "");

			final URIBuilder b;
			try {
				b = URIBuilder.fromString(url);
			} catch (URISyntaxException e) {
				e.printStackTrace();
				return null;
			}
			b.fixScheme(DEFAULT_PROTOCOL);
			if (!b.hasScheme(PROTOCOLS))
				return null;
			b.appendPath("/service/rest/");

			final URL repoURL = b.withChildPath("repositories")
					.toURL(null);
			// invalid url => empty dropdown
			if (repoURL == null) {
				if (repo != null)
					dropdown.add(new Option(repo + " (unavailable)", repo, true));
				return dropdown;
			}

			boolean found = repo == null;
			for (String repoId : List.of("repoA", "repoB", "repoC")) {
				final boolean f = !found && repo.equals(repoId);
				dropdown.add(new Option(repoId, repoId, f));
				found = found | f;
			}
			if (!found)
				dropdown.add(new Option(repo + " (unavailable)", repo, true));
			return dropdown;
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
