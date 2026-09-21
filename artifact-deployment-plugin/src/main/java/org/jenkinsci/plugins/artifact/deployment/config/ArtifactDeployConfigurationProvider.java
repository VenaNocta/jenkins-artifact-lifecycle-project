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

import java.util.Optional;
import java.util.UUID;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;

public interface ArtifactDeployConfigurationProvider extends ExtensionPoint {

	/**
	 * Asks the provider to return the repo
	 *
	 * @param run  the Run from where the repo is requested
	 * @param uuid the repo-uuid
	 * @return {@code Optional} when the provider has a definition covering the
	 *         given uuid; otherwise {@code null}
	 */
	@CheckForNull
	public Optional<ArtifactRepository> getRepoByUuid(@Nullable Run<?, ?> run, @NonNull UUID uuid);

	/**
	 * Asks the provider to return the repo
	 *
	 * @param run  the Run from where the repo is requested
	 * @param name the repo-name
	 * @return {@code Optional} when the provider has a definition covering the
	 *         given name; otherwise {@code null}
	 */
	@CheckForNull
	public Optional<ArtifactRepository> getRepoByName(@Nullable Run<?, ?> run, @NonNull String name);

	public static ExtensionList<ArtifactDeployConfigurationProvider> all() {
		return ExtensionList.lookup(ArtifactDeployConfigurationProvider.class);
	}

	@CheckForNull
	public static ArtifactRepository findRepoByUuid(@Nullable final Run<?, ?> run, @NonNull final UUID uuid) {
		Optional<ArtifactRepository> r = null;
		for (ArtifactDeployConfigurationProvider provider : all()) {
			r = provider.getRepoByUuid(run, uuid);
			if (r != null)
				break;
		}
		return r == null || r.isEmpty() ? null : r.get();
	}

	@CheckForNull
	public static ArtifactRepository findRepoByName(@Nullable final Run<?, ?> run, @NonNull final String name) {
		Optional<ArtifactRepository> r = null;
		for (ArtifactDeployConfigurationProvider provider : all()) {
			r = provider.getRepoByName(run, name);
			if (r != null)
				break;
		}
		return r == null || r.isEmpty() ? null : r.get();
	}
}
