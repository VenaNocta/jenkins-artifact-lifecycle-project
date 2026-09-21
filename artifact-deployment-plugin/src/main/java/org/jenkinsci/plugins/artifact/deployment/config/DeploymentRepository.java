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

import java.util.Set;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepositoryTypeProvider;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;

// TODO bind to the existence of an Uploader!
public interface DeploymentRepository extends ArtifactRepository {

	@CheckForNull
	public String getCredentialId();

	// some implementations support multiple types!
	public default boolean hasDeploymentSupport() {
		return true;
	}

	public static boolean isSupported(final ArtifactRepository repository) {
		if (repository instanceof DeploymentRepository repo)
			return repo.hasDeploymentSupport();
		return false;
	}

	@Extension
	public static class TypeProvider implements ArtifactRepositoryTypeProvider {

		@Override
		public Set<String> getProviderTypes(final ArtifactRepository repository) {
			// TODO bind to the existence of an Uploader!
			if (DeploymentRepository.isSupported(repository))
				return Set.of("Deploy-Target");
			return Set.of();
		}
	}
}
