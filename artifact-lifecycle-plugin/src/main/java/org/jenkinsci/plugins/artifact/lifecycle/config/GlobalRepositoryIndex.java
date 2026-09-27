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
package org.jenkinsci.plugins.artifact.lifecycle.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.UUID;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.ui.GlobalArtifactLifecycleConfigLink.ArtifactLifecycleCategory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalArtifactRepositoryIndex")
public class GlobalRepositoryIndex extends GlobalConfiguration {

	protected transient final Map<UUID, ArtifactRepository> repoMap = new LinkedHashMap<>();

	private Collection<ArtifactRepository> repos = new LinkedList<>();

	public GlobalRepositoryIndex() {
		load();
	}

	@Override
	public synchronized void load() {
		super.load();
		this.repoMap.clear();
		this.repos.stream()
				.forEachOrdered(t -> this.repoMap.put(t.getUuid(), t));
	}

	public ArtifactRepository getRepoById(final UUID uuid) {
		return this.repoMap.get(uuid);
	}

	public ArtifactRepository getRepoByName(final String name) {
		if (name == null)
			return null;
		return this.repoMap.values()
				.stream()
				.filter(s -> name.equals(s.getName()))
				.findFirst()
				.orElse(null);
	}

	public boolean containsRepoId(final UUID uuid) {
		return this.repoMap.containsKey(uuid);
	}

	public Map<UUID, ArtifactRepository> getRepoMap(final Predicate<ArtifactRepository> check) {
		return getRepoMap(check, null);
	}

	public Map<UUID, ArtifactRepository> getRepoMap(final Predicate<ArtifactRepository> check,
			final Set<Class<?>> types) {
		final Map<UUID, ArtifactRepository> repoMap = new LinkedHashMap<>();
		this.repoMap.values()
				.parallelStream()
				.map(adaptToTypes(types))
				.filter(Objects::nonNull)
				.filter(check::test)
				.forEachOrdered(v -> repoMap.put(v.getUuid(), v));
		return repoMap;
	}

	public List<ArtifactRepository> getRepos(final Predicate<ArtifactRepository> check) {
		return getRepos(check, null);
	}

	public List<ArtifactRepository> getRepos(final Predicate<ArtifactRepository> check, final Set<Class<?>> types) {
		final List<ArtifactRepository> repoLst = new LinkedList<>();
		this.repoMap.values()
				.parallelStream()
				.map(adaptToTypes(types))
				.filter(Objects::nonNull)
				.filter(check::test)
				.forEachOrdered(repoLst::add);
		return repoLst;
	}

	public static Function<ArtifactRepository, ArtifactRepository> adaptToTypes(final Set<Class<?>> types) {
		if (types == null || types.isEmpty())
			return Function.identity();
		return repo -> {
			// check the repo
			boolean ok = true;
			for (Class<?> type : types)
				ok &= type.isInstance(repo);
			if (ok)
				return repo;
			// get the adapter
			if ((repo = repo.adapter(types)) == null)
				return null;
			// check the adapter
			ok = true;
			for (Class<?> type : types)
				ok &= type.isInstance(repo);
			if (ok)
				return repo;
			return null;
		};
	}

	// used by jelly & JCasC
	public List<ArtifactRepository> getRepos() {
		return new ArrayList<>(this.repoMap.values());
	}

	@DataBoundSetter
	// used by JCasC
	public void setRepos(@NonNull final Collection<ArtifactRepository> col) {
		setReposMap(col.stream()
				.collect(Collectors.toMap(ArtifactRepository::getUuid, Function.identity())));
	}

	public synchronized void setReposMap(final Map<UUID, ArtifactRepository> repos) {
		// TODO consider using a read-write-lock
		IdentifierUtils.updateMapEntries(this.repoMap, repos);
	}

	// used by jelly
	@Override
	@Restricted(NoExternalUse.class)
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		setReposMap(IdentifierUtils.bindIdentifiedEntries(req, json, ArtifactRepository.class, this::containsRepoId,
				"repos", IdentifiedByUUID.JSON_ID_FIELD_NAME, null));
		save();
		return true;
	}

	@Override
	public synchronized void save() {
		this.repos = getRepos();
		super.save();
	}

	@Override
	public GlobalConfigurationCategory getCategory() {
		return GlobalConfigurationCategory.get(ArtifactLifecycleCategory.class);
	}

	@NonNull
	public static GlobalRepositoryIndex get() {
		return GlobalConfiguration.all()
				.getInstance(GlobalRepositoryIndex.class);
	}
}
