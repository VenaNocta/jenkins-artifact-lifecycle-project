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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;
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

public class SonatypeMavenCentralDeployExecution
		extends DeployArtifactsStepExecution<SonatypeMavenCentralDeploymentRepository> {

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

			// TODO uploadMavenBundle(client, new HttpClientContext(context), uriBuilder,
			// bundle);
			// } catch (URISyntaxException e) {
			// log(Level.SEVERE, "Invalid URI!", e);
			// getContext().onFailure(e);
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