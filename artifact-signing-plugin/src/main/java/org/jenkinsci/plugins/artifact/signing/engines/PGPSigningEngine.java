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
package org.jenkinsci.plugins.artifact.signing.engines;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.jenkinsci.plugins.artifact.signing.config.ArtifactSigningConfigurationProvider;
import org.jenkinsci.plugins.artifact.signing.steps.Messages;
import org.jenkinsci.plugins.pgpcredentials.PGPRequirementBuilder;
import org.jenkinsci.plugins.pgpcredentials.PGPSignatureGeneratorBuilder;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Fingerprinter;
import io.jenkins.plugins.httpclient.RobustHTTPClient;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.ArtifactManager;
import jenkins.util.BuildListenerAdapter;
import jenkins.util.VirtualFile;

public class PGPSigningEngine extends ASigningEngine {

	private static final long serialVersionUID = 1L;

	protected final String credentialId;

	public PGPSigningEngine(final StepContext context, final String signaturePrefix, final String signatureSuffix,
			final String credentialId) {
		super(context, signaturePrefix, signatureSuffix);
		this.credentialId = credentialId;
	}

	@Override
	public String getSignaturePrefix() {
		if (StringUtils.isBlank(this.sigPrefix))
			return "";
		return this.sigPrefix;
	}

	@Override
	public String getSignatureSuffix() {
		if (StringUtils.isBlank(this.sigSuffix))
			return "asc";
		return this.sigSuffix;
	}

	@Override
	public boolean signArtifacts(final TaskListener listener, final ArtifactManager artifactManager,
			final String artifacts, final boolean fingerprint) throws Exception {
		final Run<?, ?> build = getContext().get(Run.class);
		final EnvVars env = getContext().get(EnvVars.class);
		final Node secureNode = getSecureNode();
		final FilePath ws = getSecureWorkspace(secureNode, build);
		final VirtualFile archiveFileRoot = artifactManager.root();

		// -- load credentials --
		String credentialId = this.credentialId;
		if (StringUtils.isBlank(credentialId))
			credentialId = ArtifactSigningConfigurationProvider
					.get(ArtifactSigningConfigurationProvider::getDefaultCredentialsId, build);
		if (StringUtils.isBlank(credentialId)) {
			listener.getLogger()
					.println(Messages.PGPSigningEngine_ERR_NoKeyDefined());
			return false;
		}

		listener.getLogger()
				.println(Messages.PGPSigningEngine_SigningArtifactsWithKey(credentialId));

		// -- prepare PGP signature-generator --
		final PGPSignatureGeneratorBuilder builder = new PGPSignatureGeneratorBuilder();
		final Map<String, String> signatures = new ConcurrentHashMap<>();
		{
			final List<DomainRequirement> domainRequirements = PGPRequirementBuilder.create()
					.requireValid()
					.requireSigningCapability()
					.build();
			final PGPPrivateKeyCredentials cred = CredentialsProvider.findCredentialById(credentialId,
					PGPPrivateKeyCredentials.class, build, domainRequirements);

			if (cred == null) {
				listener.getLogger()
						.println(Messages.PGPSigningEngine_ERR_NoSigningKeyFound());
				return false;
			}
			if (!cred.isValid()) {
				listener.getLogger()
						.println(Messages.PGPSigningEngine_ERR_SigningKeyInvalid());
				return false;
			}

			builder.withCredentials(cred);
		}

		// -- create signatures --
		{
			final String prefix = getSignaturePrefix();
			final String suffix = getSignatureSuffix();
			for (String filePath : archiveFileRoot.list(artifacts, null, true)) {
				final VirtualFile archivedArtifactFile = archiveFileRoot.child(filePath);

				if (!archivedArtifactFile.isFile())
					continue;

				final String path = fullPath(archiveFileRoot, archivedArtifactFile, prefix, suffix);
				signatures.put(path, path);
				final FilePath artifactSignature = ws.child(path);
				artifactSignature.act(new PGPFileSigner(secureNode, listener, builder.build(), archivedArtifactFile));
			}
		}
		listener.getLogger()
				.println(Messages.ASigningEngine_ArtifactsSigned(signatures.size()));

		// -- archive signatures --
		final Launcher launcher = ws.createLauncher(listener);
		artifactManager.archive(ws, launcher, BuildListenerAdapter.wrap(listener), signatures);
		if (fingerprint) {
			final Fingerprinter f = new Fingerprinter(String.join(",", signatures.keySet()));
			f.setDefaultExcludes(true);
			f.setCaseSensitive(true);
			f.perform(build, ws, env, launcher, listener);
		}
		return true;
	}

	public static class PGPFileSigner extends MasterToSlaveFileCallable<Boolean> {

		private static final long serialVersionUID = 1L;

		protected final Node secureNode;
		protected final TaskListener listener;
		protected final PGPSignatureGenerator signatureGenerator;
		protected final VirtualFile source;

		public PGPFileSigner(final Node secureNode, final TaskListener listener,
				final PGPSignatureGenerator signatureGenerator, final VirtualFile source) {
			this.secureNode = secureNode;
			this.listener = listener;
			this.signatureGenerator = signatureGenerator;
			this.source = source;
		}

		@Override
		public Boolean invoke(final File file, final VirtualChannel channel) throws IOException, InterruptedException {
			final RobustHTTPClient httpClient = new RobustHTTPClient();
			final URL url = this.source.toExternalURL();
			final AtomicBoolean result = new AtomicBoolean(false);

			file.getParentFile()
					.mkdirs();

			try (final OutputStream output = ArmoredOutputStream.builder()
					.clearHeaders()
					.build(new FileOutputStream(file, false))) {
				if (url == null) {
					try (InputStream dataStream = this.source.open()) {
						result.set(signArtifact(dataStream, output));
					}
				} else {
					httpClient.connect("download",
							Messages.ASigningEngine_ArtifactDownload(RobustHTTPClient.sanitize(url),
									this.secureNode.getDisplayName()),
							client -> client.execute(new HttpGet(url.toString())), response -> {
								try (InputStream is = response.getEntity()
										.getContent()) {
									result.set(signArtifact(is, output));
								}
							}, this.listener);
				}
			}
			return result.get();
		}

		protected boolean signArtifact(InputStream inputStream, OutputStream outputStream) throws IOException {
			inputStream = new BufferedInputStream(inputStream);

			final byte[] buffer = new byte[4096];
			int len;
			while ((len = inputStream.read(buffer)) > 0) {
				this.signatureGenerator.update(buffer, 0, len);
			}

			final PGPSignature signature;
			try {
				signature = this.signatureGenerator.generate();
			} catch (PGPException e) {
				this.listener.error(Messages.PGPSigningEngine_ERR_SigningFailed(e.getMessage()));
				return false;
			}
			signature.encode(outputStream);
			outputStream.flush();
			return true;
		}
	}
}
