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
package org.jenkinsci.plugins.repoproxy.maven;

import java.util.function.Consumer;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.BomViolationException;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

public class CyclonedxValidator extends net.runeduniverse.lib.repo.maven.validation.cyclonedx.CyclonedxValidator {

	protected final Consumer<ArtifactException> pipelineKiller;

	public CyclonedxValidator(final PublicKeyIndex keyIndex, final ComponentIndex componentIndex,
			final Consumer<ArtifactException> pipelineKiller) {
		super(keyIndex, componentIndex);
		this.pipelineKiller = pipelineKiller;
	}

	@Override
	public boolean validate(final ArtifactProvider provider, final ArtifactData data) throws InvalidArtifactException {
		try {
			return super.validate(provider, data);
		} catch (BomViolationException e) {
			if (e.getPURL() == null)
				e.setPURL(data.getPURL());
			System.out.println(">>> Validation failed » " + data.getPURL());
			this.pipelineKiller.accept(e);
			throw e;
		}
	}
}
