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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import hudson.util.ListBoxModel;

@OptionalExtension(requirePlugins = { "cloudbees-folder" })
public class ArtifactSignerFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

	private boolean inheritSigners;

	@CheckForNull
	private UUID defaultSignerId;
	private List<NamedSignerConfigRef> nameMappings = Collections.emptyList();

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

	// used by JCasC
	public List<NamedSignerConfigRef> getNameMappings() {
		final List<NamedSignerConfigRef> entries = new LinkedList<>(this.nameMappings);
		entries.sort(NamedSignerConfigRef.COMPARE_BY_NAME);
		return entries;
	}

	// used by jelly
	public List<NamedSignerConfigRef> getNameMappings2() {
		return ArtifactSignerIndex.get()
				.findAllSignerNameMappings(getOwner())
				.stream()
				.filter(NamedSignerConfigRef.distinctByName())
				.sorted(NamedSignerConfigRef.COMPARE_BY_NAME)
				.collect(Collectors.toList());
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

	// used by JCasC
	@DataBoundSetter
	public void setNameMappings(final List<NamedSignerConfigRef> nameMappings) {
		this.nameMappings = nameMappings.stream()
				.filter(Objects::nonNull)
				.filter(NamedSignerConfigRef.distinctByName())
				.collect(Collectors.toList());
	}

	// used by jelly
	@DataBoundSetter
	public void setNameMappings2(final List<NamedSignerConfigRef> nameMappings) {
		this.nameMappings = nameMappings.stream()
				.filter(Objects::nonNull)
				.filter(NamedSignerConfigRef::isUnlocked)
				.filter(NamedSignerConfigRef.distinctByName())
				.collect(Collectors.toList());
	}

	@OptionalExtension(requirePlugins = { "cloudbees-folder" })
	@Symbol("artifactSignerConfig")
	public static class Descriptor extends AbstractFolderPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return Messages.FolderConfiguration_DisplayName();
		}

		@Restricted(NoExternalUse.class)
		@POST
		public ListBoxModel doFillDefaultSignerIdItems(@AncestorInPath final Item item,
				@QueryParameter final String defaultSignerId, @QueryParameter boolean inheritSigners) {
			return IdentifierUtils.allValuesAsListBox(ArtifactSignerIndex.get()
					.getSigners(item), defaultSignerId, SignerConfig::getName, true,
					inheritSigners ? "« inherited »" : IdentifierUtils.SELECT_NONE_TEXT);
		}
	}

	// should be asked first
	@OptionalExtension(requirePlugins = { "cloudbees-folder" }, ordinal = 10000)
	public static class FolderSignerNameResolver implements SignerNameResolver {

		@Override
		public Optional<UUID> resolveName(final Item item, @Nullable final String name) {
			if (item instanceof AbstractFolder) {
				final AbstractFolder<?> folder = (AbstractFolder<?>) item;
				final ArtifactSignerFolderProperty cnf = folder.getProperties()
						.get(ArtifactSignerFolderProperty.class);
				if (cnf == null)
					return null;

				if (name == null) {
					final UUID uuid = cnf.getDefaultSignerId();
					if (cnf.getInheritSigners())
						return uuid == null ? /* inherited = */ null : Optional.of(uuid);
					return Optional.ofNullable(uuid);
				}
				return cnf.getNameMappings()
						.stream()
						.filter(ref -> name.equals(ref.getName()))
						.map(NamedSignerConfigRef::getUuid)
						.findFirst()
						.or(() -> cnf.getInheritSigners() ? null : Optional.empty());
			}
			return null;
		}

		@Override
		public boolean collectNameMappings(final Item item, final Set<NamedSignerConfigRef> nameMappings) {
			Objects.requireNonNull(nameMappings);
			if (item instanceof AbstractFolder) {
				final AbstractFolder<?> folder = (AbstractFolder<?>) item;
				final ArtifactSignerFolderProperty cnf = folder.getProperties()
						.get(ArtifactSignerFolderProperty.class);
				if (cnf == null)
					return true;
				nameMappings.addAll(cnf.getNameMappings());
				return cnf.getInheritSigners();
			}
			return true;
		}
	}
}
