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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.repoproxy.config.ProxyConfig;
import org.jenkinsci.plugins.repoproxy.config.ProxyConfigDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.common.IdCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;

public class MvnProxyConfig extends ProxyConfig {

	private static final long serialVersionUID = 1L;

	protected Map<String, RepoSourceConfig> sourceMap = new LinkedHashMap<>();
	protected Set<ValidatorConfig> validatorCol = new LinkedHashSet<>();

	public MvnProxyConfig(final UUID id, final String name) {
		super(id, name);
	}

	@DataBoundConstructor
	public MvnProxyConfig(final String id, final String name) {
		super(UUID.fromString(id), name);
	}

	public List<RepoSourceConfig> getSources() {
		if (this.sourceMap == null)
			return new ArrayList<>();
		return new ArrayList<>(this.sourceMap.values());
	}

	public List<ValidatorConfig> getValidatorCol() {
		if (this.validatorCol == null)
			return new ArrayList<>();
		return new ArrayList<>(this.validatorCol);
	}

	@DataBoundSetter
	public void setSources(@NonNull final List<RepoSourceConfig> sources) {
		final List<RepoSourceConfig> newSources = new ArrayList<>(sources);
		// ignore unchanged
		this.sourceMap.values()
				.retainAll(newSources);
		newSources.removeAll(this.sourceMap.values());

		// process additions
		for (RepoSourceConfig source : newSources) {
			if (source == null)
				continue;
			// check repo id for null or duplicates
			String key = StringUtils.trimToNull(source.getKey());
			if (key == null)
				continue;
			while (this.sourceMap.containsKey(key)) {
				// if id is null, skip repo
				source.setKey(key = source.getDescriptor()
						.randomKey());
			}
			this.sourceMap.put(key, source);
		}
	}

	@DataBoundSetter
	public void setValidatorCol(final Collection<ValidatorConfig> collection) {
		if (collection == null || collection.isEmpty())
			this.validatorCol = new LinkedHashSet<>();
		else {
			this.validatorCol = new LinkedHashSet<>();
			collection.stream()
					.filter(ValidatorConfig::checkValid)
					.forEachOrdered(this.validatorCol::add);
		}
	}

	public Set<UUID> getRequiredSBoms() {
		final Set<UUID> ids = new LinkedHashSet<>();
		this.validatorCol.stream()
				.filter(Objects::nonNull)
				.map(ValidatorConfig::getRequiredSBoms)
				.filter(Objects::nonNull)
				.forEach(ids::addAll);
		return ids;
	}

	public Set<String> getRequiredCredentials() {
		final Set<String> credIds = new LinkedHashSet<>();
		this.sourceMap.values()
				.stream()
				.filter(Objects::nonNull)
				.map(RepoSourceConfig::getCredentialIds)
				.filter(Objects::nonNull)
				.forEach(credIds::addAll);
		return credIds;
	}

	public Map<String, RepositorySource> buildSourceMap(final Path repoPath, final Map<UUID, Path> sbomCacheMap,
			final Map<String, IdCredentials> credentialMap, final Consumer<ArtifactException> pipelineKiller)
			throws IOException, InterruptedException {
		final List<MetadataValidator> metadataValidators = new LinkedList<>();
		final List<ArtifactValidator> artifactValidators = new LinkedList<>();
		for (ValidatorConfig config : this.validatorCol) {
			if (!ValidatorConfig.checkValid(config))
				continue;
			final MetadataValidator metadataValidator = config.buildMetadataValidator(sbomCacheMap, pipelineKiller);
			final ArtifactValidator artifactValidator = config.buildArtifactValidator(sbomCacheMap, pipelineKiller);

			if (metadataValidator != null)
				metadataValidators.add(metadataValidator);
			if (artifactValidator != null)
				artifactValidators.add(artifactValidator);
		}
		return this.sourceMap.values()
				.stream()
				.collect(Collectors.toMap(RepoSourceConfig::getKey, c -> {
					final RepositorySource source = c.buildSource(repoPath, credentialMap);
					metadataValidators.stream()
							.forEachOrdered(source::addLastValidator);
					artifactValidators.stream()
							.forEachOrdered(source::addLastValidator);
					return source;
				}));
	}

	@Extension
	@Symbol("embeddedMavenProxy")
	public static class Descriptor extends ProxyConfigDescriptor {

		@Override
		public String getDisplayName() {
			return "Maven Repository";
		}
	}
}
