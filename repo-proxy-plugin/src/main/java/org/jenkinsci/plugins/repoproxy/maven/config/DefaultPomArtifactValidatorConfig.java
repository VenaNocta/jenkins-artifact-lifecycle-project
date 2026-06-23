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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;

public class DefaultPomArtifactValidatorConfig extends ValidatorConfig {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public DefaultPomArtifactValidatorConfig() {
		super();
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public Set<UUID> getRequiredSBoms() {
		return null;
	}

	@Override
	public MetadataValidator buildMetadataValidator(Map<UUID, Path> sbomPaths,
			Consumer<ArtifactException> pipelineKiller) throws IOException, InterruptedException {
		return null;
	}

	@Override
	public ArtifactValidator buildArtifactValidator(Map<UUID, Path> sbomPaths,
			Consumer<ArtifactException> pipelineKiller) throws IOException, InterruptedException {
		return new ArtifactValidator() {
			@Override
			public boolean validate(final ArtifactProvider provider, final ArtifactData data)
					throws InvalidArtifactException {
				return data.isPOM();
			}
		};
	}

	@Extension
	public static class Descriptor extends ValidatorConfigDescriptor {

		@Override
		public String getDisplayName() {
			return "Accept all Pom Artifacts";
		}
	}
}
