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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.GlobalArtifactLifecycleConfigLink.ArtifactLifecycleCategory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalArtifactRepositoryIndex")
public class GlobalRepositoryIndex extends GlobalConfiguration {

	protected final Map<UUID, ArtifactRepository> repos = new LinkedHashMap<>();

	public GlobalRepositoryIndex() {
		load();
	}

	public ArtifactRepository getRepoById(final UUID uuid) {
		return this.repos.get(uuid);
	}

	public ArtifactRepository getRepoByName(final String name) {
		if (name == null)
			return null;
		return this.repos.values()
				.stream()
				.filter(s -> name.equals(s.getName()))
				.findFirst()
				.orElse(null);
	}

	public boolean containsRepoId(final UUID uuid) {
		return this.repos.containsKey(uuid);
	}

	// used by jelly & JCasC
	public List<ArtifactRepository> getRepos() {
		return new ArrayList<ArtifactRepository>(this.repos.values());
	}

	@DataBoundSetter
	// used by JCasC
	public void setRepos(@NonNull final Collection<ArtifactRepository> col) {
		setReposMap(col.stream()
				.collect(Collectors.toMap(ArtifactRepository::getUuid, Function.identity())));
	}

	public synchronized void setReposMap(final Map<UUID, ArtifactRepository> repos) {
		// TODO consider using a read-write-lock
		IdentifierUtils.updateMapEntries(this.repos, repos);
	}

	// used by jelly
	@Override
	@Restricted(NoExternalUse.class)
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		System.out.println(json.toString());
		final JSONArray sourcesArray = IdentifierUtils.getOptJsonAsArray(json, "repos");
		setReposMap(IdentifierUtils.bindIdentifiedEntries(req, sourcesArray, ArtifactRepository.class,
				this::containsRepoId, this::verifyPostBind, ArtifactRepository.JSON_ID_FIELD_NAME));
		System.out.println("repos: " + this.repos.size());
		save();
		return true;
	}

	protected FormException verifyPostBind(final UUID uuid, final ArtifactRepository repo) {
		if (!uuid.equals(repo.uuid))
			return new FormException("UUID mismatch after binding", "repos");
		return null;
	}

	@NonNull
	public static GlobalRepositoryIndex get() {
		return GlobalConfiguration.all()
				.getInstance(GlobalRepositoryIndex.class);
	}

	@Override
	public GlobalConfigurationCategory getCategory() {
		return GlobalConfigurationCategory.get(ArtifactLifecycleCategory.class);
	}
}
