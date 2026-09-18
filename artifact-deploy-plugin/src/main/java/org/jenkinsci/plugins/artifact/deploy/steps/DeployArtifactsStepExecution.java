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
package org.jenkinsci.plugins.artifact.deploy.steps;

import java.io.IOException;
import java.util.function.Function;
import org.apache.http.HttpHost;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.jenkinsci.plugins.artifact.lifecycle.config.AArtifactRepository;
import org.jenkinsci.plugins.artifact.bundle.context.Bundle;
import org.jenkinsci.plugins.artifact.bundle.context.BundleContext;
import org.jenkinsci.plugins.artifact.bundle.context.Bundles;
import org.jenkinsci.plugins.artifact.bundle.steps.BundleStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.ProxyConfiguration;
import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import jenkins.util.JenkinsJVM;
import jenkins.util.SystemProperties;

public abstract class DeployArtifactsStepExecution<T extends AArtifactRepository> extends BundleStepExecution {

	private static final long serialVersionUID = 1L;

	protected final DeployArtifactsStep step;
	protected final T repoConfig;

	protected DeployArtifactsStepExecution(@NonNull StepContext context, @NonNull DeployArtifactsStep step,
			@NonNull T repoConfig) {
		super(context);
		this.step = step;
		this.repoConfig = repoConfig;
	}

	protected T getRepoConfig() {
		return this.repoConfig;
	}

	protected String getArtifactPaths() throws IOException, InterruptedException {
		return getArtifactPaths(this.step.getArtifacts());
	}

	@NonNull
	protected Bundle getSelectedBundle() throws IOException, InterruptedException {
		final String artifacts = getArtifactPaths();
		final BundleContext context = getBundleContext();
		Bundle bundle = context == null ? null : context.getBundle(this.step.getBundle());

		if (artifacts != null) {
			if (bundle == null)
				// create anonymous Bundle
				bundle = new Bundle(null);
			else
				bundle = bundle.copy();

			final ArtifactManager artifactManager = getRun().pickArtifactManager();
			Bundles.indexArtifacts(bundle, artifactManager, artifacts, null, true, this.step.getMetadata());
		}
		// if nothing special is selected, return "default" Bundle
		return bundle == null ? (context == null ? new Bundle(null) : context.getBundle()) : bundle;
	}

	protected <C extends IdCredentials> void useCreds(final Class<C> type, final String id,
			final Function<C, Boolean> consumer) throws IOException, InterruptedException {
		final String credentialsId = id;
		if (credentialsId == null)
			return;
		final Run<?, ?> run = getRun();
		final C credentials = CredentialsProvider.findCredentialById(credentialsId, type, run);
		if (credentials == null)
			throw new AbortException(String.format("Credentials (id: %s) Unavailable!", credentialsId));
		if (consumer.apply(credentials))
			CredentialsProvider.track(run, credentials);
	}

	protected ProxyConfiguration getProxyConfiguration() {
		if (!JenkinsJVM.isJenkinsJVM())
			return null;
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		return jenkins == null ? null : jenkins.proxy;
	}

	protected org.apache.http.HttpHost getProxyHost() {
		final ProxyConfiguration proxyConfig = getProxyConfiguration();
		if (proxyConfig == null)
			return null;
		final String hostname = proxyConfig.getName();
		if (hostname == null)
			return null;
		return new HttpHost(hostname, proxyConfig.getPort());
	}

	protected org.apache.http.auth.Credentials getProxyCredentials() {
		final ProxyConfiguration proxyConfig = getProxyConfiguration();
		if (proxyConfig == null)
			return null;
		final String user = proxyConfig.getUserName();
		if (user == null)
			return null;
		return new UsernamePasswordCredentials(user, proxyConfig.getSecretPassword()
				.getPlainText());
	}

	protected String getJenkinsUserAgent() {
		if (!JenkinsJVM.isJenkinsJVM()
				|| SystemProperties.getBoolean("jenkins.UserAgentURLConnectionDecorator.DISABLED"))
			return null;
		return "Jenkins/" + Jenkins.getVersion();
	}

}