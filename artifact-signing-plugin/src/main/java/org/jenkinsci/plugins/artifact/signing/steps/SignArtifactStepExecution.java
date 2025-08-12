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
package org.jenkinsci.plugins.artifact.signing.steps;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Deque;
import java.util.LinkedList;
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
import org.jenkinsci.plugins.pgpcredentials.PGPRequirementBuilder;
import org.jenkinsci.plugins.pgpcredentials.PGPSignatureGeneratorBuilder;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.remoting.VirtualChannel;
import hudson.slaves.WorkspaceList;
import hudson.tasks.Fingerprinter;
import io.jenkins.plugins.httpclient.RobustHTTPClient;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import jenkins.util.BuildListenerAdapter;
import jenkins.util.VirtualFile;

public class SignArtifactStepExecution extends SynchronousStepExecution<Void> {

	private static final long serialVersionUID = 1L;

	private final SignArtifactStep step;

	protected SignArtifactStepExecution(@NonNull StepContext context, @NonNull SignArtifactStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Void run() throws Exception {
		final Node buildNode = getContext().get(Node.class);
		final Label label = Label.get(getLabelExpression());
		final Node secureNode;
		if (label.contains(buildNode)) {
			secureNode = buildNode;
		} else {
			secureNode = Jenkins.get();
		}

		final Run<?, ?> build = getContext().get(Run.class);
		final EnvVars env = getContext().get(EnvVars.class);
		final FilePath ws = getSecureWorkspace(secureNode, build);

		final ArtifactManager artifactManager = build.pickArtifactManager();
		final VirtualFile archiveFileRoot = artifactManager.root();

		final TaskListener listener = getContext().get(TaskListener.class);

		String artifacts = this.step.getArtifacts();
		if (build instanceof AbstractBuild) { // no expansion in pipelines
			artifacts = env.expand(artifacts);
		}

		String credentialId = this.step.getCredentialId();
		if (StringUtils.isBlank(credentialId))
			credentialId = "ed25519-pgp-key";
		listener.getLogger()
				.println(String.format("Signing Artifacts with PGP-Key (%s)", credentialId));

		final PGPSignatureGeneratorBuilder builder = new PGPSignatureGeneratorBuilder();
		final Map<String, String> signatures = new ConcurrentHashMap<>();

		{
			// prepare PGP signature-generator
			final List<DomainRequirement> domainRequirements = PGPRequirementBuilder.create()
					.requireValid()
					.requireSigningCapability()
					.build();
			PGPPrivateKeyCredentials cred = CredentialsProvider.findCredentialById(credentialId,
					PGPPrivateKeyCredentials.class, build, domainRequirements);

			if (cred == null)
				throw new Exception("PGP Private Key was not found!");
			if (!cred.isValid())
				throw new Exception("PGP Private Key is not valid!");

			builder.withCredentials(cred);
		}
		{
			// create signatures
			final String prefix = this.step.getSignaturePrefix();
			final String suffix = this.step.getSignatureSuffix();
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
				.println(String.format("%d Artifacts signed", signatures.size()));
		{
			// archive signatures
			final Launcher launcher = ws.createLauncher(listener);
			artifactManager.archive(ws, launcher, BuildListenerAdapter.wrap(listener), signatures);
			if (this.step.getFingerprint()) {
				final Fingerprinter f = new Fingerprinter(String.join(",", signatures.keySet()));
				f.setDefaultExcludes(true);
				f.setCaseSensitive(true);
				f.perform(build, ws, env, launcher, listener);
			}
		}
		listener.getLogger()
				.println("All Signatures successfully archived");

		return null;
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file) {
		return fullPath(root, file, "", "");
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file, final String prefix,
			final String suffix) {
		final Deque<String> path = new LinkedList<>();
		path.add(String.format("%s%s.%s", prefix, file.getName(), suffix));
		for (VirtualFile f = file.getParent(); f != null; f = f.getParent()) {
			if (root.equals(f))
				break;
			path.addFirst(f.getName());
		}
		return String.join("/", path);
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
							"download " + RobustHTTPClient.sanitize(url) + " to " + this.secureNode.getDisplayName()
									+ " for signing",
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
				this.listener.error("Failed to create PGP-Signature: " + e.getMessage());
				return false;
			}
			signature.encode(outputStream);
			outputStream.flush();
			return true;
		}

	}

	protected FilePath getSecureWorkspace(final Node node, final Run<?, ?> run)
			throws IOException, InterruptedException {
		FilePath ws = null;
		// handle Run instances with dynamic workspaces
		if (run instanceof AbstractBuild<?, ?>) {
			final AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
			ws = build.getWorkspace();
		}
		if (run instanceof WorkflowRun) {
			final WorkflowRun pipeline = (WorkflowRun) run;
			if (pipeline.getParent() instanceof TopLevelItem) {
				ws = node.getWorkspaceFor((TopLevelItem) pipeline.getParent());
				if (ws == null) {
					throw new IOException(node.getDisplayName() + " may be offline");
				}
			}
			// else: should not happen, but just in case go to fallback
		}
		if (ws != null) {
			ws = ws.withSuffix(getFilePathSuffix() + "secure");
			try (WorkspaceList.Lease lease = node.toComputer()
					.getWorkspaceList()
					.acquire(ws)) {
				return lease.path;
			}

		}
		// as fallback use an equivalent as for the standard archive
		if (node != null)
			return node.createPath(run.getRootDir()
					.getAbsolutePath() + "/secure");
		return null;
	}

	private String getLabelExpression() {
		return "secure";
	}

	private String getFilePathSuffix() {
		return System.getProperty(WorkspaceList.class.getName(), "@");
	}
}