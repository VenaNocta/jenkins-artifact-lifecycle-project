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
package org.jenkinsci.plugins.artifact.deploy.repos;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeploymentRepository;
import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeploymentRepositoryDescriptor;
import org.jenkinsci.plugins.artifact.deploy.steps.ArtifactDeployExecutionFactory;
import org.jenkinsci.plugins.artifact.deploy.steps.DeployArtifactsStep;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

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
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import net.runeduniverse.lib.utils.common.URIBuilder;;

public class SonatypeNexusDeploymentRepository extends ArtifactDeploymentRepository {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_PROTOCOL = "https";
	public static final List<String> PROTOCOLS = List.of("http", "https");

	@CheckForNull
	private URI uri;
	@CheckForNull
	private String repo;
	@CheckForNull
	private String credentialsId;

	public SonatypeNexusDeploymentRepository(@NonNull String id, @CheckForNull String description, //
			@CheckForNull URI uri, @CheckForNull String repo, @CheckForNull String credentialsId) {
		super(id, description);
		this.uri = uri;
		this.repo = repo;
		this.credentialsId = credentialsId;
	}

	@DataBoundConstructor
	public SonatypeNexusDeploymentRepository(@NonNull String id, @CheckForNull String description, //
			@CheckForNull String uri, @CheckForNull String repo, @CheckForNull String credentialsId) {
		super(id, description);
		setUri(uri);
		this.repo = repo;
		this.credentialsId = credentialsId;
	}

	public URI getUri() {
		return this.uri;
	}

	public String getRepo() {
		return this.repo;
	}

	public String getCredentialsId() {
		return Util.fixEmpty(this.credentialsId);
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
		builder.fixScheme(DEFAULT_PROTOCOL)
				.applyHostFix();
		this.uri = builder.hasScheme(PROTOCOLS) ? builder.toURI(null) : null;
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

			final URI repoURI = builder.applyHostFix()
					.appendPath("/service/rest/v1/status")
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

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckRepo(@ContextInPath ModelObject context, @QueryParameter String value) {
			value = StringUtils.trimToNull(value);
			if (value == null)
				return FormValidation.error("Repository is required");
			return FormValidation.ok();
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
					.applyHostFix()
					.appendPath("/service/rest/");
			return b.hasScheme(PROTOCOLS) ? b : null;
		}

		public ListBoxModel doFillRepoItems(@AncestorInPath Item item, @QueryParameter String uri,
				@QueryParameter String repo, @QueryParameter String credentialsId) {
			repo = StringUtils.trimToNull(repo);

			final ListBoxModel dropdown = new ListBoxModel();
			dropdown.add("- none -", "");

			final URIBuilder b = getUriBuilder(uri);
			if (b == null) {
				if (repo != null)
					dropdown.add(new Option(repo + " (unavailable)", repo, true));
				return dropdown;
			}

			final URI repoURI = b.withChildPath("v1/repositories")
					.toURI(null);
			// invalid uri => empty dropdown
			if (repoURI == null || repoURI.getHost() == null) {
				if (repo != null)
					dropdown.add(new Option(repo + " (unavailable)", repo, true));
				return dropdown;
			}

			final HttpClient client = ProxyConfiguration.newHttpClient();
			final HttpRequest.Builder requestBuilder = ProxyConfiguration.newHttpRequestBuilder(repoURI)
					.GET();
			if ((credentialsId = StringUtils.trimToNull(credentialsId)) != null) {
				final StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull( //
						CredentialsProvider.lookupCredentialsInItem( //
								StandardUsernamePasswordCredentials.class, item, null),
						CredentialsMatchers.withId(credentialsId));
				if (cred != null) {
					requestBuilder.setHeader("Authorization",
							"Basic " + Base64.encodeBase64String((cred.getUsername() + ":" + cred.getPassword()
									.getPlainText()).getBytes()));
				}
			}
			final HttpRequest request = requestBuilder.build();

			JsonNode rootNode = null;
			try {
				final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
				if (response.statusCode() == HttpURLConnection.HTTP_OK)
					rootNode = new ObjectMapper().readTree(response.body());
			} catch (IOException | InterruptedException e) {
			}

			boolean found = repo == null;
			if (rootNode != null && rootNode instanceof ArrayNode) {
				final Map<String, List<String>> repoMap = new TreeMap<>();
				final ArrayNode arrayNode = (ArrayNode) rootNode;
				for (Iterator<JsonNode> i = arrayNode.elements(); i.hasNext();) {
					JsonNode node = i.next();
					String id = getValue(node.get("name"));
					String format = getValue(node.get("format"));
					String type = getValue(node.get("type"));
					if (id == null || format == null || type == null || !"hosted".equals(type))
						continue;
					repoMap.computeIfAbsent(format, k -> new LinkedList<>())
							.add(id);
				}

				for (Entry<String, List<String>> entry : repoMap.entrySet()) {
					final String key = entry.getKey();
					final List<String> list = entry.getValue();
					list.sort(null);
					for (String repoId : list) {
						final boolean f = !found && repo.equals(repoId);
						dropdown.add(new Option(String.format("[%s] %s", key, repoId), repoId, f));
						found = found | f;
					}
				}
			}
			if (!found)
				dropdown.add(new Option(repo + " (unavailable)", repo, true));
			return dropdown;
		}

		protected String getValue(JsonNode node) {
			if (!node.isValueNode())
				return null;
			return node.asText();
		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item) {
			if (item == null && !Jenkins.get()
					.hasPermission(Jenkins.ADMINISTER) || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
				return new StandardListBoxModel();
			}
			return new StandardListBoxModel().includeEmptyValue()
					.includeAs(ACL.SYSTEM2, item, StandardUsernamePasswordCredentials.class);
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckCredentialsId(@AncestorInPath Item item, @QueryParameter String value,
				@QueryParameter String uri) {

			final URIBuilder b = getUriBuilder(uri);
			if (b == null)
				return FormValidation.ok();

			final URI repoURI = b.appendPath("v1/status")
					.toURI(null);
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

	@Extension
	public static class SonatypeNexusFactory extends ArtifactDeployExecutionFactory<SonatypeNexusDeploymentRepository> {

		@Override
		public StepExecution create(StepContext context, DeployArtifactsStep step,
				SonatypeNexusDeploymentRepository repo) throws Exception {
			return new SonatypeNexusDeployExecution(context, step, repo);
		}

	}
}
