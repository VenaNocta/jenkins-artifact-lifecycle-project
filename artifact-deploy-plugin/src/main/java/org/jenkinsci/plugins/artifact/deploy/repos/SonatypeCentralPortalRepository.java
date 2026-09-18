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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.deploy.steps.ArtifactDeployExecutionFactory;
import org.jenkinsci.plugins.artifact.deploy.steps.DeployArtifactsStep;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepositoryDescriptor;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepositoryTags;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import net.runeduniverse.lib.utils.common.URIBuilder;

public class SonatypeCentralPortalRepository extends ArtifactRepository {

	private static final Logger LOGGER = Logger.getLogger(SonatypeCentralPortalRepository.class.getName());

	private static final long serialVersionUID = 1L;

	public static final String PUBLIC_API = "https://central.sonatype.com/api/";
	public static final String PUBLISHING_TYPE__USER_MANAGED = "USER_MANAGED";
	public static final String PUBLISHING_TYPE__AUTOMATIC = "AUTOMATIC";
	public static final URI API_URI;

	static {
		URI api = null;
		try {
			api = new URI(PUBLIC_API);
		} catch (URISyntaxException e) {
			LOGGER.log(Level.SEVERE, "Failed to convert static final API to URI!", e);
		}
		API_URI = api;
	}

	@CheckForNull
	private String namespace;
	@CheckForNull
	private String credentialsId;
	private boolean allowAutoPublish = false;
	@CheckForNull
	private String publishingType;

	@DataBoundConstructor
	public SonatypeCentralPortalRepository(@NonNull final String uuid, final String name, @Nullable String description, //
			@CheckForNull String namespace, @CheckForNull String credentialsId, @NonNull boolean allowAutoPublish,
			@Nullable String publishingType) {
		super(UUID.fromString(uuid), name, description);
		this.namespace = namespace;
		this.credentialsId = credentialsId;
		this.allowAutoPublish = allowAutoPublish;
		this.publishingType = publishingType;
		// tags
		this.tags.add(ArtifactRepositoryTags.TYPE__TARGET);
		this.tags.add(ArtifactRepositoryTags.PROTOCOL__SONATYPE_CENTRAL_PORTAL_APIv1);
	}

	public String getNamespace() {
		return this.namespace;
	}

	public String getCredentialsId() {
		return this.credentialsId;
	}

	public boolean getAllowAutoPublish() {
		return this.allowAutoPublish;
	}

	public String getPublishingType() {
		return this.publishingType;
	}

	public URI getURI() {
		return API_URI;
	}

	@DataBoundSetter
	public void setNamespace(@NonNull final String namespace) {
		this.namespace = namespace;
	}

	@DataBoundSetter
	public void setCredentialsId(@CheckForNull final String credentialsId) {
		this.credentialsId = credentialsId;
	}

	@DataBoundSetter
	public void setAllowAutoPublish(@CheckForNull final boolean allowAutoPublish) {
		this.allowAutoPublish = allowAutoPublish;
	}

	@DataBoundSetter
	public void setPublishingType(@CheckForNull final String publishingType) {
		this.publishingType = StringUtils.trimToNull(publishingType);
	}

	@Extension
	public static class SonatypeMavenCentralDescriptor extends ArtifactRepositoryDescriptor {

		@Override
		public String getDisplayName() {
			return "Sonatype Maven Central Portal";
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckNamespace(@ContextInPath ModelObject context, @QueryParameter String value) {
			value = StringUtils.trimToNull(value);
			if (value == null)
				return FormValidation.error("Namespace is required");
			return FormValidation.ok();
		}

		public ListBoxModel doFillPublishingTypeItems(@AncestorInPath Item item,
				@QueryParameter boolean allowAutoPublish, @QueryParameter String publishingType) {
			publishingType = StringUtils.trimToNull(publishingType);
			final ListBoxModel dropdown = new ListBoxModel();
			dropdown.add(new Option( //
					PUBLISHING_TYPE__USER_MANAGED, //
					PUBLISHING_TYPE__USER_MANAGED,
					publishingType == null || PUBLISHING_TYPE__USER_MANAGED.equals(publishingType) //
			));
			if (allowAutoPublish) {
				dropdown.add(new Option( //
						PUBLISHING_TYPE__AUTOMATIC, //
						PUBLISHING_TYPE__AUTOMATIC, //
						PUBLISHING_TYPE__AUTOMATIC.equals(publishingType) //
				));
			}
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

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckCredentialsId(@AncestorInPath Item item, @QueryParameter String value) {
			final URIBuilder b = new URIBuilder(API_URI);
			final URI repoURI = b.appendPath("v1/publisher/status")
					.setQuery("id=")
					.toURI(null);
			// invalid uri => skip check | theoretically impossible!
			if (repoURI == null)
				return FormValidation.ok();

			final HttpClient client = ProxyConfiguration.newHttpClient();
			final HttpRequest.Builder requestBuilder = ProxyConfiguration.newHttpRequestBuilder(repoURI)
					.POST(BodyPublishers.noBody());
			if ((value = StringUtils.trimToNull(value)) != null) {
				final StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull( //
						CredentialsProvider.lookupCredentialsInItem( //
								StandardUsernamePasswordCredentials.class, item, null),
						CredentialsMatchers.withId(value));
				if (cred != null) {
					requestBuilder.setHeader("Authorization",
							"Bearer " + Base64.encodeBase64String((cred.getUsername() + ":" + cred.getPassword()
									.getPlainText()).getBytes()));
				}
			}

			final HttpRequest request = requestBuilder.build();
			HttpResponse<String> response;
			try {
				response = client.send(request, BodyHandlers.ofString());
			} catch (IOException | InterruptedException e) {
				response = null;
			}

			switch (response == null ? -1 : response.statusCode()) {
			case 400:
				return FormValidation.error("Wrong authorization data (user/password or token)");
			case 401:
				return FormValidation.error("Invalid Credentials!");
			case 403:
				return FormValidation.warning("Credentials unauthorized!");
			case -1:
			case 404:
				// we did send no data sooo, this is expected
				return FormValidation.ok();
			case 500:
			default:
				JsonNode rootNode = null;
				try {
					rootNode = new ObjectMapper().readTree(response.body());
				} catch (JsonProcessingException e) {
				}
				String msg = null;
				if (rootNode != null) {
					final JsonNode errorNode = rootNode.get("error");
					if (errorNode != null) {
						final JsonNode msgNode = rootNode.get("message");
						if (msgNode != null) {
							if (msgNode.isValueNode())
								msg = StringUtils.trimToNull(msgNode.asText());
						}
					}
				}
				if (msg == null)
					return FormValidation.warning("Failed to verify Credentials!");
				return FormValidation.warning("Failed to verify Credentials: " + msg);
			}
		}
	}

	@Extension
	public static class SonatypeCentralPortalFactory
			extends ArtifactDeployExecutionFactory<SonatypeCentralPortalRepository> {

		@Override
		public StepExecution create(StepContext context, DeployArtifactsStep step, SonatypeCentralPortalRepository repo)
				throws Exception {
			return new SonatypeMavenCentralDeployExecution(context, step, repo);
		}
	}
}
