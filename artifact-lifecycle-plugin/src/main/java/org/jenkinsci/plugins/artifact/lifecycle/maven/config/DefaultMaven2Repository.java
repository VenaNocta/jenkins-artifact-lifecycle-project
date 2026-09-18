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
package org.jenkinsci.plugins.artifact.lifecycle.maven.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.lifecycle.config.AArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepositoryDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import com.cloudbees.plugins.credentials.ContextInPath;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.runeduniverse.lib.utils.common.URIBuilder;

public class DefaultMaven2Repository extends AArtifactRepository implements Maven2Repository {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_PROTOCOL = "https";
	public static final List<String> PROTOCOLS = List.of("http", "https", "ftp", "sftp");

	@CheckForNull
	private URI uri;
	@CheckForNull
	private String credentialId;

	public DefaultMaven2Repository(@NonNull final String uuid, final String name, @CheckForNull String description,
			@CheckForNull URI uri, @CheckForNull String credentialId) {
		super(UUID.fromString(uuid), name, description);
		this.uri = uri;
		this.credentialId = Util.fixEmptyAndTrim(credentialId);
	}

	@DataBoundConstructor
	public DefaultMaven2Repository(@NonNull final String uuid, final String name, @CheckForNull String description,
			@CheckForNull String uri, @CheckForNull String credentialId) {
		super(UUID.fromString(uuid), name, description);
		setUri(uri);
		this.credentialId = Util.fixEmptyAndTrim(credentialId);
	}

	public URI getUri() {
		return this.uri;
	}

	@Override
	public URI getMaven2Uri() {
		return getUri();
	}

	public String getCredentialId() {
		return this.credentialId;
	}

	@DataBoundSetter
	public void setUri(@NonNull String uri) {
		final URIBuilder builder;
		try {
			builder = URIBuilder.fromString(uri);
		} catch (URISyntaxException e) {
			this.uri = null;
			return;
		}
		if (builder == null) {
			this.uri = null;
			return;
		}
		builder.fixScheme(DEFAULT_PROTOCOL)
				.applyHostFix();
		this.uri = builder.hasScheme(PROTOCOLS) ? builder.toURI(null) : null;
	}

	@DataBoundSetter
	public void setCredentialId(@CheckForNull final String credentialId) {
		this.credentialId = Util.fixEmptyAndTrim(credentialId);
	}

	@Extension
	public static class Descriptor extends ArtifactRepositoryDescriptor {

		@Override
		public String getDisplayName() {
			return "Maven2 Repository";
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckUri(@AncestorInPath Item item, @ContextInPath ModelObject context,
				@QueryParameter String value, @QueryParameter String credentialsId) {
			value = StringUtils.trimToNull(value);
			if (value == null)
				return FormValidation.error("URI is required");
			final URIBuilder builder;
			try {
				builder = URIBuilder.fromString(value);
			} catch (URISyntaxException e) {
				return FormValidation.error("Invalid URI");
			}

			final List<FormValidation> msgs = new LinkedList<>();
			final String scheme = builder.getScheme();
			if (scheme == null)
				msgs.add(FormValidation.warning("Protocol must be 'https' (default) or 'http'"));
			builder.fixScheme(DEFAULT_PROTOCOL);

			if (!builder.hasScheme(PROTOCOLS))
				return FormValidation.error("Protocol unsupported; allowed Protocols: 'https' (default) or 'http'");
			if (!builder.hasScheme(Set.of("http", "https")))
				return FormValidation.warning("unable to verify repository");

			final URI repoURI = builder.applyHostFix()
					// .appendPath("archetype-catalog.xml")
					.toURI(null);
			if (repoURI == null || repoURI.getHost() == null)
				return FormValidation.error("Invalid URL");

			final HttpClient client = ProxyConfiguration.newHttpClient();
			final HttpRequest request = ProxyConfiguration.newHttpRequestBuilder(repoURI)
					.GET()
					.build();

			HttpResponse<Void> response;
			try {
				response = client.send(request, BodyHandlers.discarding());
			} catch (IOException | InterruptedException e) {
				response = null;
			}
			switch (response == null ? -1 : response.statusCode()) {
			case 200:
			case 401:
				break;
			case 503:
				msgs.add(FormValidation.error("Server unavailable to service requests!"));
				break;
			default:
				msgs.add(FormValidation.error("Server unreachable!"));
				break;
			}
			return FormValidation.aggregate(msgs);
		}

		public URIBuilder getUriBuilder(final String uri) {
			final URIBuilder b;
			try {
				b = URIBuilder.fromString(uri);
			} catch (URISyntaxException e) {
				return null;
			}
			if (b == null)
				return null;
			b.fixScheme(DEFAULT_PROTOCOL)
					.applyHostFix();
			return b.hasScheme(PROTOCOLS) ? b : null;
		}

		public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item item) {
			if (item == null && !Jenkins.get()
					.hasPermission(Jenkins.ADMINISTER) || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
				return new StandardListBoxModel();
			}
			return new StandardListBoxModel().includeEmptyValue()
					.includeAs(ACL.SYSTEM2, item, StandardUsernamePasswordCredentials.class);
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckCredentialId(@AncestorInPath Item item, @QueryParameter String value,
				@QueryParameter String uri) {
			final URIBuilder b = getUriBuilder(uri);
			if (b == null)
				return FormValidation.ok();
			if (!b.hasScheme(Set.of("http", "https")))
				return FormValidation.warning("unable to verify credentials");

			final URI repoURI = b.toURI(null);
			// invalid uri => skip check
			if (repoURI == null || repoURI.getHost() == null)
				return FormValidation.ok();

			final HttpClient client = ProxyConfiguration.newHttpClient();
			final HttpRequest.Builder requestBuilder = ProxyConfiguration.newHttpRequestBuilder(repoURI)
					.GET();
			if ((value = StringUtils.trimToNull(value)) != null) {
				final StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull( //
						CredentialsProvider.lookupCredentialsInItem( //
								StandardUsernamePasswordCredentials.class, item, null),
						CredentialsMatchers.withId(value));
				if (cred != null) {
					requestBuilder.setHeader("Authorization",
							"Basic " + Base64.encodeBase64String((cred.getUsername() + ":" + cred.getPassword()
									.getPlainText()).getBytes()));
				}
			}

			final HttpRequest request = requestBuilder.build();
			HttpResponse<Void> response;
			try {
				response = client.send(request, BodyHandlers.discarding());
			} catch (IOException | InterruptedException e) {
				response = null;
			}
			if (response != null && response.statusCode() == 401)
				return FormValidation.warning("Credentials unauthorized!");
			return FormValidation.ok();
		}
	}
}
