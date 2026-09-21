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
import org.jenkinsci.plugins.artifact.lifecycle.config.GlobalRepositoryIndex;

import com.google.inject.Inject;

import hudson.Extension;
import hudson.model.Run;

// provides the global fallback values
@Extension(ordinal = -10000)
public final class GlobalArtifactDeployConfigurationProvider implements ArtifactDeployConfigurationProvider {

	@Inject
	private GlobalRepositoryIndex cnf;

	@Override
	public Optional<ArtifactRepository> getRepoByUuid(final Run<?, ?> run, final UUID uuid) {
		return Optional.ofNullable(this.cnf.getRepoById(uuid));
	}

	@Override
	public Optional<ArtifactRepository> getRepoByName(final Run<?, ?> run, final String name) {
		return Optional.ofNullable(this.cnf.getRepoByName(name));
	}
}