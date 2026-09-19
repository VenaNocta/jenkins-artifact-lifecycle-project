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

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepositoryAdapterBuilder;
import org.jenkinsci.plugins.artifact.lifecycle.maven.config.Maven2Repository;
import org.jenkinsci.plugins.variant.OptionalExtension;

import hudson.Extension;
import net.runeduniverse.lib.utils.common.URIBuilder;

public class SonatypeNexusMaven2Adapter implements Maven2Repository {

	private static final long serialVersionUID = 1L;

	private final SonatypeNexusRepository original;

	public SonatypeNexusMaven2Adapter(final SonatypeNexusRepository original) {
		this.original = original;
	}

	@Override
	public UUID getUuid() {
		return this.original.getUuid();
	}

	@Override
	public String getName() {
		return this.original.getName();
	}

	@Override
	public URI getMaven2Uri() {
		final URI uri = this.original.getUri();
		final String repo = this.original.getRepo();
		if (uri == null || repo == null || !hasMaven2Support())
			return null;
		return new URIBuilder(uri)//
				.appendPath("/repository/" + repo)
				.toURI(null);
	}

	@Override
	public boolean hasMaven2Support() {
		return "maven2".equals(this.original.getFormat());
	}

	@Override
	public String getCredentialId() {
		return this.original.getCredentialId();
	}

	@Extension
	// @OptionalExtension(requirePlugins = { "artifact-lifecycle-maven-api" })
	public static class Factory implements SonatypeNexusRepositoryAdapterFactory {

		@Override
		public void build(ArtifactRepositoryAdapterBuilder<SonatypeNexusRepository> builder, Set<Class<?>> types) {
			builder.withHandler(new SonatypeNexusMaven2Adapter(builder.getOriginal()), Maven2Repository.class);
		}
	}
}
