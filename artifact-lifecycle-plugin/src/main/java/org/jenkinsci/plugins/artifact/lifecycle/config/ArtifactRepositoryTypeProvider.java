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

import java.util.LinkedHashSet;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

public interface ArtifactRepositoryTypeProvider extends ExtensionPoint {

	@NonNull
	public Set<String> getProviderTypes(ArtifactRepository repository);

	public static Set<String> allProviderTypes(final ArtifactRepository repository) {
		final Set<String> set = new LinkedHashSet<>();
		if (repository == null)
			return set;
		for (ArtifactRepositoryTypeProvider p : Jenkins.get()
				.getExtensionList(ArtifactRepositoryTypeProvider.class))
			set.addAll(p.getProviderTypes(repository));
		return set;
	}
}
