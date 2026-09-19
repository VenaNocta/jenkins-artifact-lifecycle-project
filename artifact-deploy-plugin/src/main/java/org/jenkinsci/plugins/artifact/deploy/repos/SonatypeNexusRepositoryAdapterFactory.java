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
package org.jenkinsci.plugins.artifact.deploy.repos;

import java.util.Set;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepositoryAdapterBuilder;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

public interface SonatypeNexusRepositoryAdapterFactory extends ExtensionPoint {

	public void build(final ArtifactRepositoryAdapterBuilder<SonatypeNexusRepository> builder,
			final Set<Class<?>> types);

	public static ArtifactRepository buildProxy(final SonatypeNexusRepository repository, final Set<Class<?>> types) {
		final ArtifactRepositoryAdapterBuilder<SonatypeNexusRepository> builder = //
				new ArtifactRepositoryAdapterBuilder<SonatypeNexusRepository>(repository);
		for (SonatypeNexusRepositoryAdapterFactory factory : Jenkins.get()
				.getExtensionList(SonatypeNexusRepositoryAdapterFactory.class))
			factory.build(builder, types);
		return builder.buildProxy();
	}
}
