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
package org.jenkinsci.plugins.repoproxy.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import hudson.util.ListBoxModel;

@OptionalExtension(requirePlugins = { "cloudbees-folder" })
public class RepoProxyFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

	private boolean inheritNameMappings;

	private List<NamedProxyConfigRef> nameMappings = Collections.emptyList();

	@DataBoundConstructor
	public RepoProxyFolderProperty() {
	}

	public boolean getInheritNameMappings() {
		return this.inheritNameMappings;
	}

	// used by JCasC
	public List<NamedProxyConfigRef> getNameMappings() {
		final List<NamedProxyConfigRef> entries = new LinkedList<>(this.nameMappings);
		entries.sort(NamedProxyConfigRef.COMPARE_BY_NAME);
		return entries;
	}

	// used by jelly
	public List<NamedProxyConfigRef> getNameMappings2() {
		return Stream.concat(this.nameMappings.stream(), RepoProxyConfiguration.getResolver()
				.findAllEntryNameMappings(getOwner())
				.stream()
				.filter(NamedRefUUID::noComment)
				.map(NamedRefUUID::asReadonly))
				.filter(NamedProxyConfigRef.distinctByName())
				.sorted(NamedRefUUID.COMPARE_BY_NAME)
				.collect(Collectors.toList());
	}

	@DataBoundSetter
	public void setInheritNameMappings(final boolean value) {
		this.inheritNameMappings = value;
	}

	// used by JCasC
	@DataBoundSetter
	public void setNameMappings(final List<NamedProxyConfigRef> nameMappings) {
		this.nameMappings = nameMappings.stream()
				.filter(Objects::nonNull)
				.filter(NamedRefUUID::noComment)
				.filter(NamedProxyConfigRef.distinctByName())
				.collect(Collectors.toList());
	}

	// used by jelly
	@DataBoundSetter
	public void setNameMappings2(final List<NamedProxyConfigRef> nameMappings) {
		this.nameMappings = nameMappings.stream()
				.filter(Objects::nonNull)
				// readonly entries, are inherited entries -> skip
				.filter(NamedRefUUID::isWritable)
				.filter(NamedRefUUID::noComment)
				.filter(NamedProxyConfigRef.distinctByName())
				.collect(Collectors.toList());
	}

	@OptionalExtension(requirePlugins = { "cloudbees-folder" })
	@Symbol("artifactProxyConfig")
	public static class Descriptor extends AbstractFolderPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return Messages.FolderConfiguration_Proxy_DisplayName();
		}

		@Restricted(NoExternalUse.class)
		@POST
		public ListBoxModel doFillDefaultProxyIdItems(@AncestorInPath final Item item,
				@QueryParameter final String defaultProxyId, @QueryParameter boolean inheritNameMappings) {
			return IdentifierUtils.allValuesAsListBox(RepoProxyConfiguration.get()
					.getConfigs(item), defaultProxyId, ProxyConfig::getName, true,
					inheritNameMappings ? "« inherited »" : IdentifierUtils.SELECT_NONE_TEXT);
		}
	}

	// should be asked first
	@OptionalExtension(requirePlugins = { "cloudbees-folder" }, ordinal = 10000)
	public static class FolderProxyNameResolver implements ProxyNameResolver {

		@Override
		public Optional<UUID> resolveName(final Item item, @Nullable final String name) {
			if (item instanceof AbstractFolder) {
				final AbstractFolder<?> folder = (AbstractFolder<?>) item;
				final RepoProxyFolderProperty cnf = folder.getProperties()
						.get(RepoProxyFolderProperty.class);
				if (cnf == null)
					return null;

				if (name == null) {
					// there's no default proxy config!
					if (cnf.getInheritNameMappings())
						return /* inherited = */ null;
					return Optional.ofNullable(null);
				}
				final Optional<UUID> selected = cnf.getNameMappings()
						.stream()
						.filter(NamedRefUUID::noComment)
						.filter(ref -> name.equals(ref.getName()))
						.map(RefUUID::getUuid)
						.findFirst();
				if (selected.isEmpty() && cnf.getInheritNameMappings())
					return /* inherited = */ null;
				return selected;
			}
			return null;
		}

		@Override
		public boolean collectNameMappings(final Item item, final Consumer<Collection<NamedProxyConfigRef>> collector) {
			Objects.requireNonNull(collector);
			if (item instanceof AbstractFolder) {
				final AbstractFolder<?> folder = (AbstractFolder<?>) item;
				final RepoProxyFolderProperty cnf = folder.getProperties()
						.get(RepoProxyFolderProperty.class);
				if (cnf == null)
					return true;
				collector.accept(cnf.getNameMappings());
				return cnf.getInheritNameMappings();
			}
			return true;
		}
	}
}
