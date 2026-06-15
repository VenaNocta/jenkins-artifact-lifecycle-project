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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.repoproxy.config.RefUUID;
import org.jenkinsci.plugins.repoproxy.config.SBomConfiguration;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import hudson.Extension;

public class CyclonedxValidatorConfig extends ValidatorConfig {

	private static final long serialVersionUID = 1L;

	protected Set<SBomConfiguration.IdRef> sourceIds = new LinkedHashSet<>();

	@DataBoundConstructor
	public CyclonedxValidatorConfig() {
	}

	public List<SBomConfiguration.IdRef> getSourceIds() {
		if (this.sourceIds == null)
			return new ArrayList<>();
		return new ArrayList<>(this.sourceIds);
	}

	@DataBoundSetter
	public void setSourceIds(final Collection<SBomConfiguration.IdRef> col) {
		if (col == null || col.isEmpty())
			this.sourceIds.clear();
		else {
			this.sourceIds = new LinkedHashSet<>();
			col.stream()
					.filter(RefUUID::checkValid)
					.forEach(this.sourceIds::add);
		}
	}

	@Override
	public Set<UUID> getRequiredSBoms() {
		return this.sourceIds.stream()
				.map(RefUUID::getValue)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean isValid() {
		return !this.sourceIds.isEmpty();
	}

	@Extension
	public static class Descriptor extends ValidatorConfigDescriptor {
	}
}
