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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.EncodingUtils;
import org.jenkinsci.plugins.artifact.deploy.config.URIBuilder;
import org.jenkinsci.plugins.artifact.deploy.context.Bundle;
import org.jenkinsci.plugins.artifact.deploy.steps.DeployArtifactsStep;
import org.jenkinsci.plugins.artifact.deploy.steps.DeployArtifactsStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import jenkins.util.VirtualFile;

public class SonatypeMavenCentralDeployExecution
		extends DeployArtifactsStepExecution<SonatypeMavenCentralDeploymentRepository> {

	public static final String[] METADATA_GROUPID = //
			new String[] { "maven.groupId", "groupId" };
	public static final String[] METADATA_ARTIFACTID = //
			new String[] { "maven.artifactId", "artifactId" };
	public static final String[] METADATA_VERSION = //
			new String[] { "maven.version", "version" };
	public static final String[] METADATA_EXTENSION = //
			new String[] { "maven.extension", "file.extension", "extension" };
	public static final String[] METADATA_CLASSIFIER = //
			new String[] { "maven.classifier", "classifier" };

	private static final long serialVersionUID = 1L;

	protected SonatypeMavenCentralDeployExecution(final StepContext context, final DeployArtifactsStep step,
			final SonatypeMavenCentralDeploymentRepository repo) {
		super(context, step, repo);
	}

	protected void useCreds(Consumer<StandardUsernamePasswordCredentials> consumer)
			throws IOException, InterruptedException {
		final String credentialsId = repoConfig.getCredentialsId();
		if (credentialsId == null)
			return;
		final StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(credentialsId,
				StandardUsernamePasswordCredentials.class, getRun());
		if (credentials == null)
			throw new IOException("Credentials Unavailable!");
		consumer.accept(credentials);
	}

	@Override
	public boolean start() throws Exception {
		final SonatypeMavenCentralDeploymentRepository repoConfig = getRepoConfig();
		final Bundle bundle = getSelectedBundle();

		if (bundle.isEmpty()) {
			log(Level.WARNING, "Skipping upload on empty Bundle!", null);
			getContext().onSuccess(null);
			return true;
		}

		final String namespace = StringUtils.trimToNull(repoConfig.getNamespace());
		if (namespace == null) {
			log(Level.SEVERE, "Namespace not set!", null);
			getContext().onFailure(new NullPointerException("Namespace not set!"));
			return true;
		}

		final URIBuilder uriBuilder = new URIBuilder(repoConfig.getURI());
		final URI uri;
		try {
			uri = uriBuilder.toURI();
		} catch (URISyntaxException e) {
			// theoretically impossible!
			log(Level.SEVERE, "Invalid Server address!", e);
			getContext().onFailure(e);
			return true;
		}

		final HttpHost portalHost = URIUtils.extractHost(uri);
		final BasicCredentialsProvider credProvider = new BasicCredentialsProvider();
		final AuthCache authCache = new BasicAuthCache();

		try {
			useCreds(cred -> {
				log(Level.INFO, "using Credentials: [" + cred.getId() + "]", null);
				credProvider.setCredentials(new AuthScope(portalHost),
						new UsernamePasswordCredentials(cred.getUsername(), cred.getPassword()
								.getPlainText()) //
				);
				authCache.put(portalHost, new BearerScheme());
			});
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

			uploadBundle(client, context, uriBuilder, bundle);
		} catch (URISyntaxException e) {
			log(Level.SEVERE, "Invalid URI!", e);
			getContext().onFailure(e);
		} catch (IOException e) {
			log(Level.SEVERE, "Failed to connect to Server!", e);
			getContext().onFailure(e);
		} catch (Exception e) {
			log(Level.SEVERE, e.getMessage(), e);
			getContext().onFailure(e);
		}
		// TODO catch Authorization-Exception
		return true;
	}

	protected void uploadBundle(final HttpClient client, final HttpClientContext context, final URIBuilder uriBuilder,
			final Bundle bundle) throws URISyntaxException {
		final URI uri = uriBuilder.withChildPath("v1/components")
				.setQuery("repository=" + getRepoConfig().getNamespace())
				.toURI();
		final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> invalidArtifacts = new LinkedHashSet<>();

		// group all artifacts by their groups
		final Map<String, Map<String, Map<String, Set<Map.Entry<VirtualFile, Map<String, Serializable>>>>>> artifactsByGroupId = new LinkedHashMap<>();
		bundle.getArtifactStream()
				.forEach(entry -> {
					final Map<String, Serializable> metadata = entry.getValue();
					String groupId = getEntryString(metadata, METADATA_GROUPID);
					final String artifactId = getEntryString(metadata, METADATA_ARTIFACTID);
					final String version = getEntryString(metadata, METADATA_VERSION);
					if (groupId == null || artifactId == null || version == null) {
						invalidArtifacts.add(entry);
						return;
					}
					// convert canonical groupId to path
					groupId = groupId.replace('.', '/');

					artifactsByGroupId.computeIfAbsent(groupId, k -> new LinkedHashMap<>())
							.computeIfAbsent(artifactId, k -> new LinkedHashMap<>())
							.computeIfAbsent(version, k -> new LinkedHashSet<>())
							.add(entry);
				});
		// validate the projects 1 by 1

		boolean errors = false;
		try (ByteArrayOutputStream dataStream = new ByteArrayOutputStream()) {
			try (TarArchiveOutputStream archiveStream = new TarArchiveOutputStream(
					new GZIPOutputStream(new BufferedOutputStream(dataStream)))) {
				for (Entry<String, Map<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>>> groupEntry : artifactsByGroupId
						.entrySet()) {
					for (Entry<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>> artifactEntry : groupEntry
							.getValue()
							.entrySet())
						for (Entry<String, Set<Entry<VirtualFile, Map<String, Serializable>>>> versionEntry : artifactEntry
								.getValue()
								.entrySet()) {
							errors = errors | packageArtifacts(archiveStream, versionEntry.getValue(), invalidArtifacts,
									groupEntry.getKey(), artifactEntry.getKey(), versionEntry.getKey());
						}
				}
			}

			dataStream.toByteArray();

			// TODO upload dataStream

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
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
		return;
	}

	protected void appendArchiveEntry(final TarArchiveOutputStream archiveStream, final String path, final byte[] data)
			throws IOException {
		final TarArchiveEntry entry = new TarArchiveEntry(path);
		entry.setSize(data.length);
		archiveStream.putArchiveEntry(entry);
		archiveStream.write(data);
		archiveStream.closeArchiveEntry();
	}

	protected boolean packageArtifacts(final TarArchiveOutputStream archiveStream,
			final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> artifacts,
			final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> invalidArtifacts, final String groupId,
			final String artifactId, final String version) throws IOException, NoSuchAlgorithmException {
		final Set<String> mainKeys = new LinkedHashSet<>();
		final Set<String> testKeys = new LinkedHashSet<>();

		for (Entry<VirtualFile, Map<String, Serializable>> entry : artifacts) {
			final Map<String, Serializable> metadata = entry.getValue();

			// verify extension
			final String extension = getEntryString(metadata, METADATA_EXTENSION);
			if (extension == null) {
				invalidArtifacts.add(entry);
				continue;
			}
			// index classifier.extension for later verification
			final String classifier = getEntryString(metadata, METADATA_CLASSIFIER);
			if ("tests".equals(classifier) || StringUtils.startsWith(classifier, "test-"))
				testKeys.add(classifier + '.' + extension);
			else {
				if (classifier == null)
					mainKeys.add(extension);
				else
					mainKeys.add(classifier + '.' + extension);
			}
			// assemble artifact name
			final StringBuilder nameBuilder = new StringBuilder(artifactId).append('-')
					.append(version);
			if (classifier != null) {
				nameBuilder.append('-')
						.append(classifier);
			}
			nameBuilder.append('.')
					.append(extension);
			final String artifactName = nameBuilder.toString();
			// download artifact from artifact-manager
			final byte[] buffer;
			try {
				buffer = download(entry.getKey(), "Downloading %s from Archive to Memory");
			} catch (IOException | InterruptedException e) {
				log(Level.SEVERE, "Failed to load Artifact from Archive!", e);
				return true;
			}
			if (buffer.length == 0)
				log(Level.WARNING, String.format("Artifact (%s) is empty!", groupId + '/' + artifactName), null);
			// add artifact to archive
			appendArchiveEntry(archiveStream, String.join("/", groupId, artifactId, version, artifactName), buffer);
			// check if checksums are required
			if (StringUtils.endsWith(extension, ".asc"))
				continue;
			// calculate checksums and archive them as well
			appendArchiveEntry(archiveStream, String.join("/", groupId, artifactId, version, (artifactName + ".md5")),
					MessageDigest.getInstance(MessageDigestAlgorithms.MD5)
							.digest(buffer));
			appendArchiveEntry(archiveStream, String.join("/", groupId, artifactId, version, (artifactName + ".sha1")),
					MessageDigest.getInstance(MessageDigestAlgorithms.SHA_1)
							.digest(buffer));
		}

		// TODO validate all completeness by main/test-Keys

		return false;
	}

	private static class BearerScheme extends BasicScheme {

		private static final long serialVersionUID = 1L;

		/**
		 * Returns textual designation of the basic authentication scheme.
		 *
		 * @return {@code basic}
		 */
		@Override
		public String getSchemeName() {
			return "bearer";
		}

		/**
		 * Produces basic authorization header for the given set of {@link Credentials}.
		 *
		 * @param credentials The set of credentials to be used for authentication
		 * @param request     The request being authenticated
		 * @throws org.apache.http.auth.InvalidCredentialsException if authentication
		 *                                                          credentials are not
		 *                                                          valid or not
		 *                                                          applicable for this
		 *                                                          authentication
		 *                                                          scheme
		 * @throws AuthenticationException                          if authorization
		 *                                                          string cannot be
		 *                                                          generated due to an
		 *                                                          authentication
		 *                                                          failure
		 *
		 * @return a basic authorization string
		 */
		@Override
		public Header authenticate(final Credentials credentials, final HttpRequest request, final HttpContext context)
				throws AuthenticationException {

			Args.notNull(credentials, "Credentials");
			Args.notNull(request, "HTTP request");
			final StringBuilder tmp = new StringBuilder();
			tmp.append(credentials.getUserPrincipal()
					.getName());
			tmp.append(":");
			tmp.append((credentials.getPassword() == null) ? "null" : credentials.getPassword());

			final Base64 base64codec = new Base64(0);
			final byte[] base64password = base64codec
					.encode(EncodingUtils.getBytes(tmp.toString(), getCredentialsCharset().name()));

			final CharArrayBuffer buffer = new CharArrayBuffer(32);
			if (isProxy()) {
				buffer.append(AUTH.PROXY_AUTH_RESP);
			} else {
				buffer.append(AUTH.WWW_AUTH_RESP);
			}
			buffer.append(": Bearer ");
			buffer.append(base64password, 0, base64password.length);

			return new BufferedHeader(buffer);
		}

		/**
		 * Returns a basic {@code Authorization} header value for the given
		 * {@link Credentials} and charset.
		 *
		 * @param credentials The credentials to encode.
		 * @param charset     The charset to use for encoding the credentials
		 *
		 * @return a basic authorization header
		 *
		 * @deprecated (4.3) use
		 *             {@link #authenticate(Credentials, HttpRequest, HttpContext)}.
		 */
		@Deprecated
		public static Header authenticate(final Credentials credentials, final String charset, final boolean proxy) {
			Args.notNull(credentials, "Credentials");
			Args.notNull(charset, "charset");

			final StringBuilder tmp = new StringBuilder();
			tmp.append(credentials.getUserPrincipal()
					.getName());
			tmp.append(":");
			tmp.append((credentials.getPassword() == null) ? "null" : credentials.getPassword());

			final byte[] base64password = Base64.encodeBase64(EncodingUtils.getBytes(tmp.toString(), charset), false);

			final CharArrayBuffer buffer = new CharArrayBuffer(32);
			if (proxy) {
				buffer.append(AUTH.PROXY_AUTH_RESP);
			} else {
				buffer.append(AUTH.WWW_AUTH_RESP);
			}
			buffer.append(": Bearer ");
			buffer.append(base64password, 0, base64password.length);

			return new BufferedHeader(buffer);
		}

		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			builder.append("BEARER [complete=")
					.append(isComplete())
					.append("]");
			return builder.toString();
		}
	}

}
