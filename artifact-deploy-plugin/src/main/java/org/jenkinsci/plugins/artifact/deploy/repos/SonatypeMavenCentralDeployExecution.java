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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BufferedHeader;
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

	public static final String[] OPTION_PUBLISHING_TYPE = //
			new String[] { "publishingType", "publishingMode" };
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
	// KEYS = <extension> or <classifier>.<extension>
	public static final String[] BASE_KEYS = //
			new String[] { "pom", "pom.asc" };
	public static final String[] MAIN_KEYS = //
			new String[] { "jar", "jar.asc", //
					"javadoc.jar", "javadoc.jar.asc", //
					"sources.jar", "sources.jar.asc" //
			};
	public static final String[] TEST_KEYS = //
			new String[] { "tests.jar", "tests.jar.asc", //
					"test-javadoc.jar", "test-javadoc.jar.asc", //
					"test-sources.jar", "test-sources.jar.asc" //
			};

	private static final long serialVersionUID = 1L;

	protected SonatypeMavenCentralDeployExecution(final StepContext context, final DeployArtifactsStep step,
			final SonatypeMavenCentralDeploymentRepository repo) {
		super(context, step, repo);
	}

	protected boolean ignoreMissingArtifacts() {
		return false;
	}

	protected String getPublishingType() {
		final SonatypeMavenCentralDeploymentRepository repoConfig = getRepoConfig();
		final String defaultValue = StringUtils.trimToNull(repoConfig.getPublishingType());
		String selected = getEntryString(this.step.getOptions(), OPTION_PUBLISHING_TYPE);
		// when null, select default value
		if (selected == null)
			selected = defaultValue;
		// when null, return safest default value
		if (selected == null)
			return SonatypeMavenCentralDeploymentRepository.PUBLISHING_TYPE__USER_MANAGED;
		// check if AUTOMATIC is enabled, reset in case it is disabled & selected
		if (!repoConfig.getAllowAutoPublish()
				&& SonatypeMavenCentralDeploymentRepository.PUBLISHING_TYPE__AUTOMATIC.equals(selected)) {
			if (SonatypeMavenCentralDeploymentRepository.PUBLISHING_TYPE__AUTOMATIC.equals(defaultValue))
				return SonatypeMavenCentralDeploymentRepository.PUBLISHING_TYPE__USER_MANAGED;
			return defaultValue;
		}
		return selected;
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
		try {
			uriBuilder.toURI();
		} catch (URISyntaxException e) {
			// theoretically impossible!
			log(Level.SEVERE, "Invalid Server address!", e);
			getContext().onFailure(e);
			return true;
		}

		final BasicCredentialsProvider credProvider = new BasicCredentialsProvider();
		final List<Header> defaultHeaders = new LinkedList<>();

		// set target credentials
		try {
			useCreds(cred -> {
				log(Level.INFO, "using Credentials: [" + cred.getId() + "]", null);
				final StringBuilder tmp = new StringBuilder();
				tmp.append(cred.getUsername());
				tmp.append(":");
				tmp.append(cred.getPassword()
						.getPlainText());

				final Base64 base64codec = new Base64(0);
				final byte[] base64password = base64codec
						.encode(EncodingUtils.getBytes(tmp.toString(), Consts.ASCII.name()));

				final CharArrayBuffer buffer = new CharArrayBuffer(32);
				buffer.append(AUTH.WWW_AUTH_RESP);
				buffer.append(": Bearer ");
				buffer.append(base64password, 0, base64password.length);

				defaultHeaders.add(new BufferedHeader(buffer));
			});
		} catch (Throwable t) {
			log(Level.SEVERE, "Failed to load Credentials from Credentials-Store!", t);
			getContext().onFailure(t);
			return true;
		}

		final HttpClientBuilder clientBuilder = HttpClientBuilder.create();

		// set Proxy
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

		// handle upload
		try (final CloseableHttpClient client = clientBuilder //
				.setDefaultHeaders(defaultHeaders)
				.setDefaultCredentialsProvider(credProvider)
				.build()) {
			uploadBundle(client, context, uriBuilder, bundle);
			getContext().onSuccess(null);
		} catch (URISyntaxException e) {
			log(Level.SEVERE, "Invalid URI!", e);
			getContext().onFailure(e);
		} catch (IOException e) {
			log(Level.SEVERE, "Connection lost!", e);
			getContext().onFailure(e);
		} catch (Exception e) {
			log(Level.SEVERE, e.getMessage(), e);
			getContext().onFailure(e);
		}
		// TODO catch Authorization-Exception
		return true;
	}

	protected List<String> groupIdAsList(final String groupId) {
		final LinkedList<String> groupIdList = new LinkedList<>();
		for (String group : StringUtils.split(groupId, '.'))
			groupIdList.addLast(group);
		return groupIdList;
	}

	protected void uploadBundle(final HttpClient client, final HttpClientContext context, final URIBuilder uriBuilder,
			final Bundle bundle) throws Exception {
		final Map<String, Map<String, Map<String, Set<Map.Entry<VirtualFile, Map<String, Serializable>>>>>> artifactsByGroupId = new LinkedHashMap<>();
		// group all artifacts by their groups
		{
			final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> invalidArtifacts = new LinkedHashSet<>();
			bundle.getArtifactStream()
					.forEach(entry -> {
						final Map<String, Serializable> metadata = entry.getValue();
						final String groupId = getEntryString(metadata, METADATA_GROUPID);
						final String artifactId = getEntryString(metadata, METADATA_ARTIFACTID);
						final String version = getEntryString(metadata, METADATA_VERSION);
						final String extension = getEntryString(metadata, METADATA_EXTENSION);
						if (groupId == null || artifactId == null || version == null || extension == null) {
							invalidArtifacts.add(entry);
							return;
						}

						artifactsByGroupId.computeIfAbsent(groupId, k -> new LinkedHashMap<>())
								.computeIfAbsent(artifactId, k -> new LinkedHashMap<>())
								.computeIfAbsent(version, k -> new LinkedHashSet<>())
								.add(entry);
					});
			if (!invalidArtifacts.isEmpty()) {
				log(Level.WARNING, invalidArtifacts.size() + " Artifacts with invalid Metadata found!", null);
				for (Entry<VirtualFile, Map<String, Serializable>> entry : invalidArtifacts)
					log(Level.WARNING, "  - " + entry.getKey()
							.getName(), null);
				log(Level.INFO, "", null);
			}
		}
		// validate namespace
		{
			final String namespace = getRepoConfig().getNamespace();
			for (Iterator<String> i = artifactsByGroupId.keySet()
					.iterator(); i.hasNext();) {
				final String groupId = i.next();
				if (groupId.startsWith(namespace))
					continue;
				log(Level.WARNING, "Skipping groupId=" + groupId, null);
				i.remove();
			}
		}
		// validate the existance of all required artifacts per project
		{
			boolean errors = false;
			for (Iterator<Entry<String, Map<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>>>> groupIterator = artifactsByGroupId
					.entrySet()
					.iterator(); groupIterator.hasNext();) {
				final Entry<String, Map<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>>> groupEntry = groupIterator
						.next();
				for (Iterator<Entry<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>>> artifactIterator = groupEntry
						.getValue()
						.entrySet()
						.iterator(); artifactIterator.hasNext();) {
					final Entry<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>> artifactEntry = artifactIterator
							.next();
					for (Iterator<Entry<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>> versionIterator = artifactEntry
							.getValue()
							.entrySet()
							.iterator(); versionIterator.hasNext();) {
						final Entry<String, Set<Entry<VirtualFile, Map<String, Serializable>>>> versionEntry = versionIterator
								.next();
						if (validateProject(versionEntry.getValue(), //
								groupEntry.getKey(), artifactEntry.getKey(), versionEntry.getKey())) {
							errors |= true;
							versionIterator.remove();
						}
					}
					if (artifactEntry.getValue()
							.isEmpty())
						artifactIterator.remove();
				}
				if (groupEntry.getValue()
						.isEmpty())
					groupIterator.remove();
			}
			if (errors) {
				if (ignoreMissingArtifacts())
					log(Level.WARNING, "Continuing deploy without invalid projects", null);
				else
					throw new Exception("Some Projects can not be deployed!");
			}
		}
		// check if there are any projects left to be deployed!
		if (artifactsByGroupId.isEmpty()) {
			log(Level.WARNING, "No deployable Projects found, skipping Upload!", null);
			return;
		}
		// build bundle name from common groupId
		final String bundleName;
		{
			final Iterator<String> i = artifactsByGroupId.keySet()
					.iterator();
			// seed common group Id's
			final List<String> groupIdList = groupIdAsList(i.next());
			// remove non matching starting at the 1st difference
			i.forEachRemaining(groupId -> {
				final Iterator<String> commonIterator = groupIdList.iterator();
				final Iterator<String> specificIterator = groupIdAsList(groupId).iterator();
				// step through all common entries
				while (commonIterator.hasNext() && specificIterator.hasNext()) {
					if (StringUtils.equals(commonIterator.next(), specificIterator.next()))
						continue;
					// remove 1st difference
					commonIterator.remove();
					break;
				}
				// remove all later entries
				while (commonIterator.hasNext()) {
					commonIterator.next();
					commonIterator.remove();
				}
			});
			bundleName = String.join("-", groupIdList) + ".tar.gz";
		}

		// compress artifacts
		try (ByteArrayOutputStream dataStream = new ByteArrayOutputStream()) {
			try (TarArchiveOutputStream archiveStream = new TarArchiveOutputStream(
					new GZIPOutputStream(new BufferedOutputStream(dataStream)))) {
				// enable long filename support
				archiveStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
				// package the projects 1 by 1
				for (Entry<String, Map<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>>> groupEntry : artifactsByGroupId
						.entrySet()) {
					for (Entry<String, Map<String, Set<Entry<VirtualFile, Map<String, Serializable>>>>> artifactEntry : groupEntry
							.getValue()
							.entrySet())
						for (Entry<String, Set<Entry<VirtualFile, Map<String, Serializable>>>> versionEntry : artifactEntry
								.getValue()
								.entrySet()) {
							packageProject(archiveStream, versionEntry.getValue(),
									// convert canonical groupId to path
									groupEntry.getKey()
											.replace('.', '/'), //
									artifactEntry.getKey(), versionEntry.getKey());
						}
				}
			}

			// validate upload data
			final byte[] data = dataStream.toByteArray();
			if (data.length > 1073741824) {
				log(Level.WARNING, "Upload > 1GB; Failure Expected!", null);
			}

			// upload dataStream
			final URI uri = uriBuilder.withChildPath("v1/publisher/upload")
					.setQuery("publishingType=" + getPublishingType())
					.toURI();
			final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

			builder.setMimeSubtype("form-data");
			builder.addBinaryBody("bundle", data, ContentType.APPLICATION_OCTET_STREAM, bundleName);

			final HttpPost request = new HttpPost(uri);
			request.setEntity(builder.build());

			final HttpResponse response = client.execute(request, context);
			final StatusLine statusLine = response.getStatusLine();
			if (statusLine == null)
				// return as error as something in the connection broke and we have no clue what
				// happened!
				throw new IOException();

			switch (statusLine.getStatusCode()) {
			case 201:
				// OK
				// Body -> The deployment identifier for created deployment.
				break;
			case 400:
				throw new Exception("Invalid Credentials!");
			case 401:
				throw new Exception("User unauthenticated!");
			case 403:
				throw new Exception("User unauthorized! Check the Namespace!");
			default:
				throw new Exception(
						"Upload returned: (" + statusLine.getStatusCode() + ") " + statusLine.getReasonPhrase());
			}
		} catch (NoSuchAlgorithmException e) {
			throw new Exception("Hash Algorithm not found!", e);
		}
		log(Level.INFO, "Upload completed!", null);
	}

	protected boolean validateProject(final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> artifacts,
			final String groupId, final String artifactId, final String version) {
		final Set<String> missingArtifacts = new HashSet<>(Set.of(BASE_KEYS));
		final Set<String> mainKeys = new LinkedHashSet<>();
		final Set<String> testKeys = new LinkedHashSet<>();

		for (Iterator<Entry<VirtualFile, Map<String, Serializable>>> i = artifacts.iterator(); i.hasNext();) {
			final Entry<VirtualFile, Map<String, Serializable>> entry = i.next();
			final Map<String, Serializable> metadata = entry.getValue();

			// index classifier.extension for later verification
			final String classifier = getEntryString(metadata, METADATA_CLASSIFIER);
			final String extension = getEntryString(metadata, METADATA_EXTENSION);
			if ("tests".equals(classifier) || StringUtils.startsWith(classifier, "test-"))
				testKeys.add(classifier + '.' + extension);
			else if (classifier == null) {
				if (missingArtifacts.remove(extension)) {
					// required base-key found
				} else
					mainKeys.add(extension);
			} else
				mainKeys.add(classifier + '.' + extension);
		}

		// validate overall completeness by main/test-keys
		if (!mainKeys.isEmpty()) {
			// if main artifacts exist
			// 1. add all main key requirements
			missingArtifacts.addAll(Set.of(MAIN_KEYS));
			// 2. remove all found main-keys
			missingArtifacts.removeAll(mainKeys);
		}
		if (!testKeys.isEmpty()) {
			// if test artifacts exist
			// 1. add all test key requirements
			missingArtifacts.addAll(Set.of(TEST_KEYS));
			// 2. remove all found test-keys
			missingArtifacts.removeAll(testKeys);
		}
		if (missingArtifacts.isEmpty())
			return false;
		log(Level.SEVERE, "Missing Artifacts detected:", null);
		final String prefix = String.format("  - %s/%s-%s-", groupId, artifactId, version);
		for (String artifact : missingArtifacts)
			log(Level.SEVERE, prefix + artifact, null);
		log(Level.INFO, "", null);
		return true;
	}

	protected void packageProject(final TarArchiveOutputStream archiveStream,
			final Set<Map.Entry<VirtualFile, Map<String, Serializable>>> artifacts, final String groupId,
			final String artifactId, final String version) throws Exception {
		for (Entry<VirtualFile, Map<String, Serializable>> entry : artifacts) {
			final Map<String, Serializable> metadata = entry.getValue();
			final String extension = getEntryString(metadata, METADATA_EXTENSION);
			final String classifier = getEntryString(metadata, METADATA_CLASSIFIER);

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
				throw new Exception("Failed to load Artifact from Archive!", e);
			}
			if (buffer.length == 0)
				log(Level.WARNING, String.format("Artifact (%s) is empty!", groupId + '/' + artifactName), null);
			// add artifact to archive
			final String path = String.join("/", groupId, artifactId, version, artifactName);
			appendArchiveEntry(archiveStream, path, buffer);
			// check if checksums are required
			if (StringUtils.endsWith(extension, ".asc"))
				continue;
			// calculate checksums and archive them as well
			appendHashEntry(archiveStream, path, buffer, "md5", MessageDigestAlgorithms.MD5);
			appendHashEntry(archiveStream, path, buffer, "sha1", MessageDigestAlgorithms.SHA_1);
			appendHashEntry(archiveStream, path, buffer, "sha256", MessageDigestAlgorithms.SHA_256);
			appendHashEntry(archiveStream, path, buffer, "sha512", MessageDigestAlgorithms.SHA_512);
		}
	}

	protected void appendArchiveEntry(final TarArchiveOutputStream archiveStream, final String path, final byte[] data)
			throws IOException {
		final TarArchiveEntry entry = new TarArchiveEntry(path);
		entry.setSize(data.length);
		archiveStream.putArchiveEntry(entry);
		archiveStream.write(data);
		archiveStream.closeArchiveEntry();
	}

	protected void appendHashEntry(final TarArchiveOutputStream archiveStream, final String path, final byte[] data,
			final String suffix, final String algorithm) throws NoSuchAlgorithmException, IOException {
		appendArchiveEntry(archiveStream, String.join(".", path, suffix),
				Hex.encodeHexString(MessageDigest.getInstance(algorithm)
						.digest(data))
						.getBytes());
	}

}
