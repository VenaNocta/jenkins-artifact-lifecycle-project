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
package org.jenkinsci.plugins.artifact.signing.config;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pgpcredentials.PGPRequirementBuilder;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

@OptionalExtension(requirePlugins = { "cloudbees-folder" })
public class ArtifactSigningFolderConfiguration extends AbstractFolderProperty<AbstractFolder<?>> {

	@CheckForNull
	private String defaultCredentialsId;

	@DataBoundConstructor
	public ArtifactSigningFolderConfiguration() {
	}

	public String getDefaultCredentialsId() {
		return Util.fixEmpty(this.defaultCredentialsId);
	}

	@DataBoundSetter
	public void setDefaultCredentialsId(@CheckForNull final String defaultCredentialsId) {
		this.defaultCredentialsId = defaultCredentialsId;
	}

	@OptionalExtension(requirePlugins = { "cloudbees-folder" })
	// @Extension(optional = true)
	@Symbol("artifactSigningConfig")
	public static class ArtifactSigningFolderConfigurationDescriptor extends AbstractFolderPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return Messages.FolderConfiguration_DisplayName();
		}

		public ListBoxModel doFillDefaultCredentialsIdItems(@AncestorInPath Item item) {
			if (item == null && !Jenkins.get()
					.hasPermission(Jenkins.ADMINISTER) || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
				return new StandardListBoxModel();
			}
			return new StandardListBoxModel().includeEmptyValue()
					.includeAs(ACL.SYSTEM2, item, PGPPrivateKeyCredentials.class, PGPRequirementBuilder.create()
							.requireSigningCapability()
							.build());
		}

	}

	// should be asked first
	// @Extension(optional = true, ordinal = 10000)
	@OptionalExtension(requirePlugins = { "cloudbees-folder" }, ordinal = 10000)
	public static class ArtifactSigningFolderConfigurationProvider implements ArtifactSigningConfigurationProvider {

		@Override
		public String getDefaultCredentialsId(@Nullable final Run<?, ?> run) {
			if (run == null)
				return null;

			final Job<?, ?> job = run.getParent();
			for (ItemGroup<?> itemGroup = job.getParent(); itemGroup != null; //
					itemGroup = itemGroup instanceof Item ? ((Item) itemGroup).getParent() : null) {

				if (itemGroup instanceof AbstractFolder) {
					final AbstractFolder<?> folder = (AbstractFolder<?>) itemGroup;
					final ArtifactSigningFolderConfiguration cnf = folder.getProperties()
							.get(ArtifactSigningFolderConfiguration.class);
					if (cnf != null) {
						final String id = cnf.getDefaultCredentialsId();
						if (!StringUtils.isBlank(id))
							return id;
					}
				}
			}
			return null;
		}

	}
}
