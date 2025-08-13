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

import java.net.URL;

import org.jenkinsci.plugins.artifact.deploy.config.AbstractArtifactDeploymentRepository;
import org.jenkinsci.plugins.artifact.deploy.config.ArtifactDeploymentRepositoryDescriptor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class SonatypeNexusDeploymentRepository extends AbstractArtifactDeploymentRepository {

	private static final long serialVersionUID = 1L;

	@NonNull
	private URL url;
	@NonNull
	private String repo;
	@CheckForNull
	private String credentialsId;

	@DataBoundConstructor
	public SonatypeNexusDeploymentRepository(@NonNull String id, @NonNull URL url, @NonNull String repo,
			@CheckForNull String credentialsId) {
		super(id);
		this.url = url;
		this.repo = repo;
		this.credentialsId = credentialsId;
	}

	public URL getUrl() {
		return this.url;
	}

	public String getRepo() {
		return this.repo;
	}

	public String getCredentialsId() {
		return Util.fixEmpty(this.credentialsId);
	}

	@DataBoundSetter
	public void setUrl(@NonNull final URL url) {
		this.url = url;
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

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item) {
			if (item == null && !Jenkins.get()
					.hasPermission(Jenkins.ADMINISTER) || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
				return new StandardListBoxModel();
			}
			return new StandardListBoxModel().includeEmptyValue()
					.includeAs(ACL.SYSTEM2, item, StandardUsernamePasswordCredentials.class);
		}

	}
}
