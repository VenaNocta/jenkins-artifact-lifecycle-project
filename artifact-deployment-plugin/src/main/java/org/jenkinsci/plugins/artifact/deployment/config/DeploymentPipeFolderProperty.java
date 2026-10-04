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
package org.jenkinsci.plugins.artifact.deployment.config;

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
import org.jenkinsci.plugins.artifact.lifecycle.config.NamedRefUUID;
import org.jenkinsci.plugins.artifact.lifecycle.config.RefUUID;
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
public class DeploymentPipeFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

	private boolean inheritPipes;

	@CheckForNull
	private UUID defaultPipeId;
	private List<NamedPipeRef> nameMappings = Collections.emptyList();

	@DataBoundConstructor
	public DeploymentPipeFolderProperty() {
	}

	public boolean getInheritPipes() {
		return this.inheritPipes;
	}

	@CheckForNull
	public UUID getDefaultPipeId() {
		return this.defaultPipeId;
	}

	// used by JCasC
	public List<NamedPipeRef> getNameMappings() {
		final List<NamedPipeRef> entries = new LinkedList<>(this.nameMappings);
		entries.sort(NamedPipeRef.COMPARE_BY_NAME);
		return entries;
	}

	// used by jelly
	public List<NamedPipeRef> getNameMappings2() {
		return DeploymentPipeIndex.getResolver()
				.findAllEntryNameMappings(getOwner())
				.stream()
				.filter(NamedRefUUID::noComment)
				.filter(NamedRefUUID.distinctByName())
				.sorted(NamedRefUUID.COMPARE_BY_NAME)
				.collect(Collectors.toList());
	}

	public void setDefaultPipeIdValue(final UUID value) {
		this.defaultPipeId = value;
	}

	@DataBoundSetter
	public void setInheritPipes(final boolean value) {
		this.inheritPipes = value;
	}

	@DataBoundSetter
	public void setDefaultPipeId(final String value) {
		this.defaultPipeId = IdentifierUtils.fixInvalidUUID(value);
	}

	// used by JCasC
	@DataBoundSetter
	public void setNameMappings(final List<NamedPipeRef> nameMappings) {
		this.nameMappings = nameMappings.stream()
				.filter(Objects::nonNull)
				.filter(NamedRefUUID::noComment)
				.filter(NamedRefUUID.distinctByName())
				.collect(Collectors.toList());
	}

	// used by jelly
	@DataBoundSetter
	public void setNameMappings2(final List<NamedPipeRef> nameMappings) {
		this.nameMappings = nameMappings.stream()
				.filter(Objects::nonNull)
				// readonly entries, are inherited entries -> skip
				.filter(NamedRefUUID::isWritable)
				.filter(NamedRefUUID::noComment)
				.filter(NamedRefUUID.distinctByName())
				.collect(Collectors.toList());
	}

	@OptionalExtension(requirePlugins = { "cloudbees-folder" })
	@Symbol("deploymentPipeConfig")
	public static class Descriptor extends AbstractFolderPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return Messages.FolderConfiguration_DisplayName();
		}

		@Restricted(NoExternalUse.class)
		@POST
		public ListBoxModel doFillDefaultPipeIdItems(@AncestorInPath final Item item,
				@QueryParameter final String defaultPipeId, @QueryParameter boolean inheritPipes) {
			return IdentifierUtils.allValuesAsListBox(DeploymentPipeIndex.get()
					.getPipes(item), defaultPipeId, PipeConfig::getName, true);
		}
	}

	// should be asked first
	@OptionalExtension(requirePlugins = { "cloudbees-folder" }, ordinal = 10000)
	public static class FolderPipeNameResolver implements PipeNameResolver {

		@Override
		public Optional<UUID> resolveName(final Item item, @Nullable final String name) {
			if (item instanceof AbstractFolder) {
				final AbstractFolder<?> folder = (AbstractFolder<?>) item;
				final DeploymentPipeFolderProperty cnf = folder.getProperties()
						.get(DeploymentPipeFolderProperty.class);
				if (cnf == null)
					return null;

				if (name == null) {
					final UUID uuid = cnf.getDefaultPipeId();
					if (cnf.getInheritPipes())
						return uuid == null ? /* inherited = */ null : Optional.of(uuid);
					return Optional.ofNullable(uuid);
				}
				return cnf.getNameMappings()
						.stream()
						.filter(NamedRefUUID::noComment)
						.filter(ref -> name.equals(ref.getName()))
						.map(RefUUID::getUuid)
						.findFirst()
						.or(() -> cnf.getInheritPipes() ? null : Optional.empty());
			}
			return null;
		}

		@Override
		public boolean collectNameMappings(final Item item, final Set<NamedPipeRef> nameMappings) {
			Objects.requireNonNull(nameMappings);
			if (item instanceof AbstractFolder) {
				final AbstractFolder<?> folder = (AbstractFolder<?>) item;
				final DeploymentPipeFolderProperty cnf = folder.getProperties()
						.get(DeploymentPipeFolderProperty.class);
				if (cnf == null)
					return true;
				nameMappings.addAll(cnf.getNameMappings());
				return cnf.getInheritPipes();
			}
			return true;
		}
	}
}
