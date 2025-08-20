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
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

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
			new String[] { "maven2.extension", "maven.extension", "file.extension", "extension" };
	public static final String[] METADATA_MAVEN2_CLASSIFIER = //
			new String[] { "maven2.classifier", "maven.classifier", "classifier" };

	private static final long serialVersionUID = 1L;

	protected SonatypeNexusDeployExecution(final StepContext context, final DeployArtifactsStep step,
			final SonatypeNexusDeploymentRepository repo) {
		super(context, step, repo);
	}

	protected void useCreds(Consumer<StandardUsernamePasswordCredentials> consumer) {
		final String credentialsId = repoConfig.getCredentialsId();
		if (credentialsId == null)
			return;
		final StandardUsernamePasswordCredentials credentials;
		try {
			credentials = CredentialsProvider.findCredentialById(credentialsId,
					StandardUsernamePasswordCredentials.class, getRun());
		} catch (IOException | InterruptedException e) {
			return;
		}
		if (credentials == null)
			return;
		consumer.accept(credentials);
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

		final URIBuilder uriBuilder = new URIBuilder(repoConfig.getUri()).appendPath("/service/rest/");
		final URI uri;
		try {
			uri = uriBuilder.toURI();
		} catch (URISyntaxException e) {
			log(Level.SEVERE, "Invalid Server address!", e);
			getContext().onFailure(e);
			return true;
		}

		final HttpHost nexusHost = URIUtils.extractHost(uri);
		final BasicCredentialsProvider credProvider = new BasicCredentialsProvider();
		final AuthCache authCache = new BasicAuthCache();

		useCreds(cred -> {
			log(Level.INFO, "using Credentials: [" + cred.getId() + "]", null);
			credProvider.setCredentials(new AuthScope(nexusHost),
					new UsernamePasswordCredentials(cred.getUsername(), cred.getPassword()
							.getPlainText()) //
			);
			authCache.put(nexusHost, new BasicScheme());
		});

		final HttpClientBuilder clientBuilder = HttpClientBuilder.create();

		if (getProxyHost() instanceof HttpHost proxyHost) {
			log(Level.INFO, "using Proxy Route", null);
			clientBuilder.setRoutePlanner(new DefaultProxyRoutePlanner(proxyHost));
			if (getProxyCredentials() instanceof Credentials proxyCreds) {
				log(Level.INFO, "using Proxy Authentication", null);
				credProvider.setCredentials(new AuthScope(proxyHost), proxyCreds);
			}
		}

		final HttpClientContext context = HttpClientContext.create();
		context.setAuthCache(authCache);

		try (final CloseableHttpClient client = clientBuilder //
				.setDefaultCredentialsProvider(credProvider)
				.build()) {

			final HttpGet request = new HttpGet(uriBuilder.withChildPath("v1/repositories")
					.toURI());
			CloseableHttpResponse response = client.execute(nexusHost, request, context);
			HttpEntity responseEntity = response.getEntity();
			responseEntity.getContent()
					.transferTo(getTaskListener().getLogger());

			log(Level.INFO, "", null);
			log(Level.INFO, "", null);
			log(Level.INFO, "", null);
			log(Level.INFO, "", null);

			// ----------------------------------------------------------------------

			// TODO check REPO exists & type

			switch ("maven2") {
			case "maven2":
				return uploadMavenBundle(client, uriBuilder, bundle);

			default:
				log(Level.WARNING, "Artifact Repository not supported!", null);
				getContext().setResult(Result.UNSTABLE);
				getContext().onSuccess(null);
				break;
			}

			getTaskListener().getLogger()
					.println("Sonatype Exec!");
			getContext().onSuccess(null);
		}
		return true;
	}

	protected boolean uploadMavenBundle(final HttpClient client, final URIBuilder uriBuilder, final Bundle bundle) {
		final URI uri;
		try {
			uri = uriBuilder.withChildPath("v1/components")
					.setQuery("repository=" + getRepoConfig().getRepo())
					.toURI();
		} catch (URISyntaxException e) {
			log(Level.SEVERE, "Invalid URI!", e);
			getContext().onFailure(e);
			return true;
		}
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
					errors = errors | uploadMavenArtifacts(client, uri, versionEntry.getValue(), invalidArtifacts,
							groupEntry.getKey(), artifactEntry.getKey(), versionEntry.getKey());
				}
		}

		if (!invalidArtifacts.isEmpty())
			log(Level.WARNING, invalidArtifacts.size() + " Artifacts with invalid Metadata found!", null);
		if (errors) {
			log(Level.SEVERE, "Upload failed!", null);
			getContext().onFailure(new Exception("Upload failed!"));
		} else {
			log(Level.INFO, "Upload completed!", null);
			getContext().onSuccess(null);
		}
		return true;
	}

	protected boolean uploadMavenArtifacts(final HttpClient client, final URI uri,
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
		/*
		 * useCreds(cred -> { request.setHeader("Authorization", "Basic " +
		 * Base64.encodeBase64String((cred.getUsername() + ":" + cred.getPassword()
		 * .getPlainText()).getBytes())); });
		 */
		request.setEntity(builder.build());

		try {
			final HttpResponse response = client.execute(request);

			try {
				getTaskListener().getLogger()
						.println("-----------------------------------------");
				getTaskListener().getLogger()
						.println("    RESPONSE");
				getTaskListener().getLogger()
						.println("-----------------------------------------");
				response.getEntity()
						.getContent()
						.transferTo(getTaskListener().getLogger());
			} catch (UnsupportedOperationException | InterruptedException | NullPointerException e) {
			}

			final StatusLine statusLine = response.getStatusLine();
			if (statusLine == null)
				// return without errors, because we have no clue what happened!
				return false;

			switch (statusLine.getStatusCode()) {
			case 200:
			case 204:
				// OK
				return false;
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