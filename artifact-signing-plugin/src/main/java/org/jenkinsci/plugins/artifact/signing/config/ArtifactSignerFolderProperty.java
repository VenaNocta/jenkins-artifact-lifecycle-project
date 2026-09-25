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

import java.util.Optional;
import java.util.UUID;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Item;

@OptionalExtension(requirePlugins = { "cloudbees-folder" })
public class ArtifactSignerFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

	private boolean inheritSigners;

	@CheckForNull
	private UUID defaultSignerId;

	@DataBoundConstructor
	public ArtifactSignerFolderProperty() {
	}

	@CheckForNull
	public boolean getInheritSigners() {
		return this.inheritSigners;
	}

	@CheckForNull
	public UUID getDefaultSignerId() {
		return this.defaultSignerId;
	}

	public void setDefaultSignerIdValue(final UUID value) {
		this.defaultSignerId = value;
	}

	@DataBoundSetter
	public void setInheritSigners(final boolean value) {
		this.inheritSigners = value;
	}

	@DataBoundSetter
	public void setDefaultSignerId(final String value) {
		this.defaultSignerId = IdentifierUtils.fixInvalidUUID(value);
	}

	@OptionalExtension(requirePlugins = { "cloudbees-folder" })
	@Symbol("artifactSignerConfig")
	public static class ArtifactSigningFolderConfigurationDescriptor extends AbstractFolderPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return Messages.FolderConfiguration_DisplayName();
		}

	}

	// provides the global fallback values
	@Extension(ordinal = -10000)
	public static class GlobalSignerNameResolver implements SignerNameResolver {

		@Inject
		private ArtifactSignerIndex index;

		@Override
		public Optional<UUID> resolveName(@Nullable final Item item, @Nullable final String name) {
			if (item instanceof AbstractFolder) {
				final AbstractFolder<?> folder = (AbstractFolder<?>) item;
				final ArtifactSignerFolderProperty cnf = folder.getProperties()
						.get(ArtifactSignerFolderProperty.class);
				if (cnf == null)
					return null;

				if (name == null)
					return Optional.ofNullable(cnf.getDefaultSignerId());
				return this.index.getSignersAsStream(item)
						.filter(signer -> name.equals(signer.getName()))
						.map(SignerConfig::getUuid)
						.findFirst()
						.or(() -> cnf.getInheritSigners() ? null : Optional.empty());
			}
			return null;
		}
	}
}
