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
package org.jenkinsci.plugins.repoproxy.maven.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.jenkinsci.plugins.repoproxy.config.RefUUID;
import org.jenkinsci.plugins.repoproxy.config.SBomSourceProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex;

public abstract class ACyclonedxValidatorConfig extends ValidatorConfig {

	private static final long serialVersionUID = 1L;

	protected Set<IdRef> sourceIds = new LinkedHashSet<>();

	protected boolean ignoreExcludedComponents = false;
	protected boolean ignoreTestComponents = false;

	protected ACyclonedxValidatorConfig() {
		super();
	}

	public List<IdRef> getSourceIds() {
		if (this.sourceIds == null)
			return new ArrayList<>();
		return new ArrayList<>(this.sourceIds);
	}

	public boolean getIgnoreExcludedComponents() {
		return this.ignoreExcludedComponents;
	}

	public boolean getIgnoreTestComponents() {
		return this.ignoreTestComponents;
	}

	@DataBoundSetter
	public void setSourceIds(final Collection<IdRef> col) {
		if (col == null || col.isEmpty())
			this.sourceIds.clear();
		else {
			this.sourceIds = new LinkedHashSet<>();
			col.stream()
					.filter(RefUUID::checkValid)
					.forEachOrdered(this.sourceIds::add);
		}
	}

	@DataBoundSetter
	public void setIgnoreExcludedComponents(final boolean ignoreExcludedDependency) {
		this.ignoreExcludedComponents = ignoreExcludedDependency;
	}

	@DataBoundSetter
	public void setIgnoreTestComponents(final boolean ignoreTestDependency) {
		this.ignoreTestComponents = ignoreTestDependency;
	}

	@Override
	public Set<UUID> getRequiredSBoms() {
		return this.sourceIds.stream()
				.map(RefUUID::getUuidValue)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean isValid() {
		return !this.sourceIds.isEmpty();
	}

	public List<Bom> getSBomList(final Map<UUID, Path> sbomPaths) {
		final List<Bom> col = new LinkedList<>();
		this.sourceIds.stream()
				.map(ref -> ref.parseBoms(sbomPaths.get(ref.getUuidValue())))
				.forEach(col::addAll);
		return col;
	}

	protected Predicate<Component> buildFilter() {
		Predicate<Component> filter = null;
		if (this.ignoreExcludedComponents)
			filter = ComponentIndex.ignoreExcludedDependency();
		if (this.ignoreTestComponents)
			filter = combine(filter, ComponentIndex.ignoreTestDependency());
		return filter;
	}

	protected static Predicate<Component> combine(final Predicate<Component> a, final Predicate<Component> b) {
		return a == null ? b : a.and(b);
	}

	@Override
	public MetadataValidator buildMetadataValidator(final Map<UUID, Path> sbomPaths,
			final Consumer<ArtifactException> pipelineKiller) throws IOException, InterruptedException {
		return null;
	}

	@Override
	public ArtifactValidator buildArtifactValidator(final Map<UUID, Path> sbomPaths,
			final Consumer<ArtifactException> pipelineKiller) throws IOException, InterruptedException {
		return null;
	}

	public static final class IdRef extends RefUUID {

		protected String filterPath = null;

		@DataBoundConstructor
		public IdRef() {
			super();
		}

		public String getFilterPath() {
			return this.filterPath;
		}

		@DataBoundSetter
		public void setFilterPath(final String filterPath) {
			this.filterPath = Util.fixEmptyAndTrim(filterPath);
		}

		@Override
		public boolean isValid() {
			return super.isValid()
					&& (this.filterPath == null || !Descriptor.PROHIBITED_DOUBLE_DOT.matcher(this.filterPath)
							.matches());
		}

		@Override
		public boolean equals(final Object obj) {
			return this == obj || obj instanceof IdRef other //
					&& StringUtils.compare(this.filterPath, other.filterPath, true) == 0 //
					&& super.equals(obj);
		}

		// NOTE: Bom instances have to be handled on node as they are not serializable!
		public List<Bom> parseBoms(Path sbomPath) {
			if (sbomPath == null || !isValid())
				return Collections.emptyList();
			if (this.filterPath != null)
				sbomPath = sbomPath.resolve(this.filterPath);

			final boolean isDir = Files.isDirectory(sbomPath, LinkOption.NOFOLLOW_LINKS);
			if (!(isDir || Files.isRegularFile(sbomPath, LinkOption.NOFOLLOW_LINKS)))
				return Collections.emptyList();
			final List<Bom> col = new LinkedList<>();

			if (isDir) {
				// TODO walk file-tree & parse sbom files
			} else {
				// TODO parse sbom file
			}
			return col;
		}

		@Extension
		public static final class Descriptor extends DescriptorRefUUID {

			/**
			 * Matches ".." in positions where it would be treated as the parent directory.
			 *
			 * <p>
			 * Used to prevent {@link #filterPath} from being used for directory traversal.
			 */
			protected static final Pattern PROHIBITED_DOUBLE_DOT = Pattern.compile("(^|.*[\\\\/])\\.\\.($|[\\\\/].*)");

			@Override
			public ListBoxModel allValuesAsListBox(final Item item) {
				return SBomSourceProvider.allSourcesAsListBox(item);
			}

			@Restricted(NoExternalUse.class)
			@POST
			public FormValidation doCheckFilterPath(@QueryParameter final String value) {
				if (value == null)
					return FormValidation.ok();
				if (PROHIBITED_DOUBLE_DOT.matcher(value)
						.matches())
					return FormValidation.error("Filter path may not contain '..'");
				return FormValidation.ok();
			}
		}
	}
}
