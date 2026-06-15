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
		else
			this.sourceIds = new LinkedHashSet<>(col);
	}

	@Extension
	public static class Descriptor extends ValidatorConfigDescriptor {
		/*
		 * public ListBoxModel doFillSourceIdsItems(@AncestorInPath Item
		 * item, @QueryParameter String[] sourceIds) { final ListBoxModel model =
		 * SBomSourceProvider.allSourcesAsListBox(item); final Set<String> selectedIds;
		 * if (sourceIds == null) selectedIds = Collections.emptySet(); else selectedIds
		 * = Arrays.asList(sourceIds) .stream() .map(Util::fixEmptyAndTrim)
		 * .filter(Objects::nonNull) .collect(Collectors.toSet());
		 *
		 * System.out.println("SelectedIds: " + String.join(", ", selectedIds));
		 *
		 * if (!selectedIds.isEmpty()) { model.forEach(o -> { if
		 * (selectedIds.contains(o.value)) o.selected = true; }); } return model; }
		 */
	}
}
