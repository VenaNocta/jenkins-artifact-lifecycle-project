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

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.jenkinsci.plugins.artifact.deploy.config.URIBuilder;
import org.jenkinsci.plugins.artifact.deploy.context.Bundle;
import org.jenkinsci.plugins.artifact.deploy.steps.DeployArtifactsStep;
import org.jenkinsci.plugins.artifact.deploy.steps.DeployArtifactsStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import hudson.AbortException;
import hudson.model.Result;
import jenkins.util.VirtualFile;

public class SonatypeNexusDeployExecution extends DeployArtifactsStepExecution<SonatypeNexusDeploymentRepository> {

	public static final String[] METADATA_MAVEN2_GROUPID = //
			new String[] { "maven2.groupId", "maven.groupId", "groupId" };
	public static final String[] METADATA_MAVEN2_ARTIFACTID = //
			new String[] { "maven2.artifactId", "maven.artifactId", "artifactId" };
	public static final String[] METADATA_MAVEN2_VERSION = //
			new String[] { "maven2.version", "maven.version", "version" };
	public static final String[] METADATA_MAVEN2_EXTENSION = //
			new String[] { "maven2.extension", "maven.extension", "extension", Bundle.METADATA_FILE_EXTENSION };
	public static final String[] METADATA_MAVEN2_CLASSIFIER = //
			new String[] { "maven2.classifier", "maven.classifier", "classifier" };

	private static final long serialVersionUID = 1L;

	protected SonatypeNexusDeployExecution(final StepContext context, final DeployArtifactsStep step,
			final SonatypeNexusDeploymentRepository repo) {
		super(context, step, repo);
	}

	protected void useCreds(Function<StandardUsernamePasswordCredentials, Boolean> consumer)
			throws IOException, InterruptedException {
		final String credentialsId = repoConfig.getCredentialsId();
		if (credentialsId == null)
			return;
		super.useCreds(StandardUsernamePasswordCredentials.class, credentialsId, consumer);
	}

	@Override
	public boolean start() throws Exception {
		final SonatypeNexusDeploymentRepository repoConfig = getRepoConfig();
		final Bundle bundle = getSelectedBundle();

		if (bundle.isEmpty()) {
			log(Level.WARNING, "Skipping upload on empty Bundle!", null);
			getContext().onSuccess(null);
			return true;
		}

		final String repo = StringUtils.trimToNull(repoConfig.getRepo());
		if (repo == null)
			throw new AbortException("Namespace not set!");

		final URIBuilder uriBuilder = new URIBuilder(repoConfig.getUri()).appendPath("/service/rest/");
		final URI uri;
		try {
			uri = uriBuilder.toURI();
		} catch (URISyntaxException e) {
			// theoretically impossible!
			throw new AbortException("Invalid Server address!");
		}

		final HttpHost nexusHost = URIUtils.extractHost(uri);
		final BasicCredentialsProvider credProvider = new BasicCredentialsProvider();
		final AuthCache authCache = new BasicAuthCache();

		try {
			useCreds(cred -> {
				log(Level.INFO, "using Credentials: [" + cred.getId() + "]", null);
				credProvider.setCredentials(new AuthScope(nexusHost),
						new UsernamePasswordCredentials(cred.getUsername(), cred.getPassword()
								.getPlainText()) //
				);
				authCache.put(nexusHost, new BasicScheme());
				return true;
			});
		} catch (AbortException e) {
			throw e;
		} catch (Throwable t) {
			log(Level.SEVERE, "Failed to load Credentials from Credentials-Store!", t);
			getContext().onFailure(t);
			return true;
		}

		final HttpClientBuilder clientBuilder = HttpClientBuilder.create();

		{
			final HttpHost proxyHost = getProxyHost();
			if (proxyHost != null) {
				log(Level.INFO, "using Proxy Route", null);
				clientBuilder.setRoutePlanner(new DefaultProxyRoutePlanner(proxyHost));
				final Credentials proxyCreds = getProxyCredentials();
				if (proxyCreds != null) {
					log(Level.INFO, "using Proxy Authentication", null);
					credProvider.setCredentials(new AuthScope(proxyHost), proxyCreds);
				}
			}
		}

		final HttpClientContext context = HttpClientContext.create();
		context.setAuthCache(authCache);

		try (final CloseableHttpClient client = clientBuilder //
				.setDefaultCredentialsProvider(credProvider)
				.build()) {
			switch (getRepoFormat(client, context, uriBuilder, repo)) {
			case "maven2":
				uploadMavenBundle(client, new HttpClientContext(context), uriBuilder, bundle);
				break;
			default:
				log(Level.WARNING, "Artifact Repository not supported!", null);
				getContext().setResult(Result.UNSTABLE);
				getContext().onSuccess(null);
				break;
			}
		}
		// known validation or operation errors
		catch (AbortException e) {
			throw e;
		}
		// TODO catch Authorization-Exception
		// unexpected errors
		catch (URISyntaxException e) {
			log(Level.SEVERE, "Invalid URI!", e);
			getContext().onFailure(e);
		} catch (IOException e) {
			log(Level.SEVERE, "Connection lost!", e);
			getContext().onFailure(e);
		} catch (Exception e) {
			log(Level.SEVERE, e.getMessage(), e);
			getContext().onFailure(e);
		}
		return true;
	}

	protected String getRepoFormat(final HttpClient client, final HttpClientContext context,
			final URIBuilder uriBuilder, final String repo) throws Exception {
		final HttpGet request = new HttpGet(uriBuilder.withChildPath("v1/repositories")
				.toURI());
		final HttpResponse response = client.execute(request, context);
		final StatusLine statusLine = response.getStatusLine();
		final HttpEntity responseEntity = response.getEntity();

		int statusCode = -1;
		if (statusLine != null && responseEntity != null)
			statusCode = statusLine.getStatusCode();

		switch (statusCode) {
		case 200:
			try (InputStream dataStream = responseEntity.getContent()) {
				final JsonNode rootNode = new ObjectMapper().readTree(dataStream);
				if (rootNode != null && rootNode instanceof ArrayNode) {
					final ArrayNode arrayNode = (ArrayNode) rootNode;
					for (Iterator<JsonNode> i = arrayNode.elements(); i.hasNext();) {
						final JsonNode node = i.next();
						final String id = getValue(node.get("name"));
						final String format = getValue(node.get("format"));
						final String type = getValue(node.get("type"));
						if (repo.equals(id) && format != null && "hosted".equals(type))
							return format;
					}
					throw new AbortException("Repositories not found!");
				}
			}
			// else -> fallback to no content
		case -1:
		case 204:
		default:
			throw new AbortException("No Repositories available!");
		}
	}

	protected String getValue(JsonNode node) {
		if (!node.isValueNode())
			return null;
		return node.asText();
	}

	protected void uploadMavenBundle(final HttpClient client, final HttpClientContext context,
			final URIBuilder uriBuilder, final Bundle bundle) throws URISyntaxException {
		final URI uri = uriBuilder.withChildPath("v1/components")
				.setQuery("repository=" + getRepoConfig().getRepo())
				.toURI();
		final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> invalidArtifacts = new LinkedHashSet<>();

		// group all artifacts by their groups
		final Map<String, Map<String, Map<String, Set<Map.Entry<VirtualFile, Map<String, Serializable>>>>>> artifactsByGroupId = new LinkedHashMap<>();
		bundle.getArtifactStream()
				.forEach(entry -> {
					final Map<String, Serializable> metadata = entry.getValue();
					final String groupId = getEntryString(metadata, METADATA_MAVEN2_GROUPID);
					final String artifactId = getEntryString(metadata, METADATA_MAVEN2_ARTIFACTID);
					final String version = getEntryString(metadata, METADATA_MAVEN2_VERSION);
					if (groupId == null || artifactId == null || version == null) {
						invalidArtifacts.add(entry);
						return;
					}

					artifactsByGroupId.computeIfAbsent(groupId, k -> new LinkedHashMap<>())
							.computeIfAbsent(artifactId, k -> new LinkedHashMap<>())
							.computeIfAbsent(version, k -> new LinkedHashSet<>())
							.add(entry);
				});
		// upload the projects 1 by 1
		boolean errors = false;
		for (Entry<String, Map<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>>> groupEntry : artifactsByGroupId
				.entrySet()) {
			for (Entry<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>> artifactEntry : groupEntry
					.getValue()
					.entrySet())
				for (Entry<String, Set<Entry<VirtualFile, Map<String, Serializable>>>> versionEntry : artifactEntry
						.getValue()
						.entrySet()) {
					errors = errors | uploadMavenArtifacts(client, context, uri, versionEntry.getValue(),
							invalidArtifacts, groupEntry.getKey(), artifactEntry.getKey(), versionEntry.getKey());
				}
		}

		if (!invalidArtifacts.isEmpty())
			log(Level.WARNING, invalidArtifacts.size() + " Artifacts with invalid Metadata found!", null);
		if (errors)
			new AbortException("Upload failed!");
		else {
			log(Level.INFO, "Upload completed!", null);
			getContext().onSuccess(null);
		}
		return;
	}

	protected boolean uploadMavenArtifacts(final HttpClient client, final HttpClientContext context, final URI uri,
			final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> artifacts,
			final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> invalidArtifacts, final String groupId,
			final String artifactId, final String version) {
		final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

		builder.setMimeSubtype("form-data");
		builder.addTextBody("maven2.groupId", groupId, ContentType.TEXT_PLAIN);
		builder.addTextBody("maven2.artifactId", artifactId, ContentType.TEXT_PLAIN);
		builder.addTextBody("maven2.version", version, ContentType.TEXT_PLAIN);

		int assetCnt = 1;
		for (Entry<VirtualFile, Map<String, Serializable>> entry : artifacts) {
			final Map<String, Serializable> metadata = entry.getValue();

			final String extension = getEntryString(metadata, METADATA_MAVEN2_EXTENSION);
			final String classifier = getEntryString(metadata, METADATA_MAVEN2_CLASSIFIER);
			if (extension == null) {
				invalidArtifacts.add(entry);
				continue;
			}

			final StringBuilder nameBuilder = new StringBuilder(artifactId).append('-')
					.append(version);
			if (classifier != null)
				nameBuilder.append('-')
						.append(classifier);
			nameBuilder.append('.')
					.append(extension);

			final byte[] buffer;
			try {
				buffer = download(entry.getKey(), "Downloading %s from Archive to Memory");
			} catch (IOException | InterruptedException e) {
				log(Level.SEVERE, "Failed to load Artifact from Archive!", e);
				return true;
			}
			if (buffer.length == 0) {
				log(Level.SEVERE, "Found empty Artifact!", null);
				return true;
			}
			builder.addBinaryBody("maven2.asset" + assetCnt, buffer, ContentType.APPLICATION_OCTET_STREAM,
					nameBuilder.toString());
			if (classifier != null)
				builder.addTextBody("maven2.asset" + assetCnt + ".classifier", classifier, ContentType.TEXT_PLAIN);
			builder.addTextBody("maven2.asset" + assetCnt++ + ".extension", extension, ContentType.TEXT_PLAIN);
		}

		final HttpPost request = new HttpPost(uri);
		request.setEntity(builder.build());

		try {
			final HttpResponse response = client.execute(request, context);
			final StatusLine statusLine = response.getStatusLine();
			if (statusLine == null)
				// return without errors, because we have no clue what happened!
				return false;

			switch (statusLine.getStatusCode()) {
			case 200:
			case 204:
				// OK
				return false;
			case 400:
				// Bad Request
				// [{"id":"*","message":"Not authorized for requested path
				// '/xxx/yyy/0.0.0/yyy-0.0.0.jar'"}]
				JsonNode rootNode = null;
				final HttpEntity data = response.getEntity();
				if (data != null) {
					rootNode = new ObjectMapper().readTree(data.getContent());
				}
				if (rootNode != null && rootNode instanceof ArrayNode arrayNode) {
					for (Iterator<JsonNode> i = arrayNode.elements(); i.hasNext();) {
						final String msg = StringUtils.trimToNull(getValue(i.next()
								.get("message")));
						if (msg != null)
							log(Level.WARNING, "Bad Request: " + msg, null);
					}
				}
			case 403:
				log(Level.SEVERE, "Insufficient permissions to upload a component!", null);
				return true;
			case 422:
				log(Level.SEVERE, "Parameter 'repository' is required or invalid 'repository'!", null);
				return true;
			default:
				log(Level.WARNING,
						"Upload returned: (" + statusLine.getStatusCode() + ") " + statusLine.getReasonPhrase(), null);
				log(Level.WARNING, "URI: " + uri.toString(), null);
			}
		} catch (IOException e) {
			log(Level.SEVERE, "Upload crashed! Issue unknown!", e);
			return true;
		}
		return false;
	}

}