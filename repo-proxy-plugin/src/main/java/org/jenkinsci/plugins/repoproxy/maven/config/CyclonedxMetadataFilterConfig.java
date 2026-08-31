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
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Extension;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.CyclonedxMetadataFilter;

// NOTE: CyclonedxMetadataFilter removes every component that it does not know from every artifact-metadata index!
public class CyclonedxMetadataFilterConfig extends ACyclonedxValidatorConfig {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public CyclonedxMetadataFilterConfig() {
		super();
	}

	@Override
	public MetadataValidator buildMetadataValidator(final Map<UUID, Path> bomPaths,
			final Consumer<Serializable> callback) throws IOException, InterruptedException {
		Predicate<Component> filter = buildFilter();
		if (filter == null)
			filter = c -> true;

		final ComponentIndex componentIndex = new ComponentIndex();
		for (Bom bom : getBomList(bomPaths)) {
			if (bom == null)
				continue;
			componentIndex.addBom(bom, filter);
		}

		return new CyclonedxMetadataFilter(componentIndex);
	}

	@Extension
	@Symbol("cyclonedxMetadataFilter")
	public static class Descriptor extends ValidatorConfigDescriptor {

		@Override
		public String getDisplayName() {
			return "Cyclonedx Metadata Filter";
		}
	}
}
