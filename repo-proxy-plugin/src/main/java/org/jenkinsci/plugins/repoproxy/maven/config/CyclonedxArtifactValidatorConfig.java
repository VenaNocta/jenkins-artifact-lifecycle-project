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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.repoproxy.maven.CyclonedxValidator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

public class CyclonedxArtifactValidatorConfig extends ACyclonedxValidatorConfig {

	private static final long serialVersionUID = 1L;

	protected boolean skipMissingArtifacts = false;
	protected boolean skipMissingPomArtifacts = false;

	protected boolean enableKeyserverOpenPgp = false;
	protected boolean enableKeyserverUbuntu = false;
	protected boolean enableKeyserverMit = false;

	@DataBoundConstructor
	public CyclonedxArtifactValidatorConfig() {
		super();
	}

	public boolean getSkipMissingArtifacts() {
		return this.skipMissingArtifacts;
	}

	public boolean getSkipMissingPomArtifacts() {
		return this.skipMissingPomArtifacts;
	}

	public boolean getEnableKeyserverOpenPgp() {
		return this.enableKeyserverOpenPgp;
	}

	public boolean getEnableKeyserverUbuntu() {
		return this.enableKeyserverUbuntu;
	}

	public boolean getEnableKeyserverMit() {
		return this.enableKeyserverMit;
	}

	@DataBoundSetter
	public void setSkipMissingArtifacts(final boolean skipMissingArtifacts) {
		this.skipMissingArtifacts = skipMissingArtifacts;
	}

	@DataBoundSetter
	public void setSkipMissingPomArtifacts(final boolean skipPomArtifacts) {
		this.skipMissingPomArtifacts = skipPomArtifacts;
	}

	@DataBoundSetter
	public void setEnableKeyserverOpenPgp(final boolean enableKeyserverOpenPgp) {
		this.enableKeyserverOpenPgp = enableKeyserverOpenPgp;
	}

	@DataBoundSetter
	public void setEnableKeyserverUbuntu(final boolean enableKeyserverUbuntu) {
		this.enableKeyserverUbuntu = enableKeyserverUbuntu;
	}

	@DataBoundSetter
	public void setEnableKeyserverMit(final boolean enableKeyserverMit) {
		this.enableKeyserverMit = enableKeyserverMit;
	}

	public PublicKeyIndex getPublicKeyIndex() {
		final PublicKeyIndex index = new PublicKeyIndex(3, 5);
		if (this.enableKeyserverOpenPgp)
			PublicKeyIndex.addKeyserverOpenPGP(index);
		if (this.enableKeyserverUbuntu)
			PublicKeyIndex.addKeyserverUbuntu(index);
		if (this.enableKeyserverMit)
			PublicKeyIndex.addKeyserverMIT(index);
		return index;
	}

	@Override
	public ArtifactValidator buildArtifactValidator(final Map<UUID, Path> bomPaths,
			final Consumer<ArtifactException> pipelineKiller) throws IOException, InterruptedException {
		Predicate<Component> filter = buildFilter();

		if (filter == null)
			filter = c -> true;

		final ComponentIndex componentIndex = new ComponentIndex();
		for (Bom bom : getBomList(bomPaths)) {
			if (bom == null)
				continue;
			componentIndex.addBom(bom, filter);
		}
		return new CyclonedxValidator(getPublicKeyIndex(), componentIndex, pipelineKiller)
				.setSkipMissing(this.skipMissingArtifacts)
				.setIgnorePom(this.skipMissingPomArtifacts);
	}

	@Extension
	@Symbol("cyclonedxArtifactValidator")
	public static class Descriptor extends ValidatorConfigDescriptor {

		@Override
		public String getDisplayName() {
			return "Cyclonedx Artifact Validator";
		}
	}
}
