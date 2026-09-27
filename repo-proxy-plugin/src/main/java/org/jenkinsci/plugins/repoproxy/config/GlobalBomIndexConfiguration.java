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
package org.jenkinsci.plugins.repoproxy.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.artifact.lifecycle.config.RefUUID;
import org.jenkinsci.plugins.artifact.lifecycle.ui.GlobalArtifactLifecycleConfigLink.ArtifactLifecycleCategory;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalBomIndex")
public class GlobalBomIndexConfiguration extends GlobalConfiguration {

	protected transient final Map<UUID, BomSource> sourceMap = new LinkedHashMap<>();

	private Collection<BomSource> sources = new LinkedList<>();

	public GlobalBomIndexConfiguration() {
		load();
	}

	@Override
	public synchronized void load() {
		super.load();
		this.sourceMap.clear();
		this.sources.stream()
				.forEachOrdered(t -> this.sourceMap.put(t.getUuid(), t));
	}

	public BomSource getSourceById(final UUID uuid) {
		return this.sourceMap.get(uuid);
	}

	public BomSource getSourceByName(final String name) {
		if (name == null)
			return null;
		return this.sourceMap.values()
				.stream()
				.filter(s -> name.equals(s.getName()))
				.findFirst()
				.orElse(null);
	}

	public boolean containsSourceId(final UUID uuid) {
		return this.sourceMap.containsKey(uuid);
	}

	// used by jelly & JCasC
	public List<BomSource> getSources() {
		return new ArrayList<BomSource>(this.sourceMap.values());
	}

	@DataBoundSetter
	// used by JCasC
	public void setSources(@NonNull final Collection<BomSource> col) {
		setSourcesMap(col.stream()
				.collect(Collectors.toMap(BomSource::getUuid, Function.identity())));
	}

	public void setSourcesMap(final Map<UUID, BomSource> sources) {
		// TODO consider using a read-write-lock
		IdentifierUtils.updateMapEntries(this.sourceMap, sources);
	}

	@Override
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		this.setSourcesMap(IdentifierUtils.bindIdentifiedEntries(req, json, BomSource.class,
				this.sourceMap::containsKey, "sources", BomSource.JSON_ID_FIELD_NAME, null));
		save();
		return true;
	}

	@Override
	public synchronized void save() {
		this.sources = getSources();
		super.save();
	}

	@Override
	public GlobalConfigurationCategory getCategory() {
		return GlobalConfigurationCategory.get(ArtifactLifecycleCategory.class);
	}

	@NonNull
	public static GlobalBomIndexConfiguration get() {
		return GlobalConfiguration.all()
				.getInstance(GlobalBomIndexConfiguration.class);
	}

	@Extension(ordinal = -10000)
	public static final class GlobalBomSourceProvider implements BomSourceProvider {

		@Override
		public BomSource getSourceById(final Run<?, ?> run, final UUID uuid) {
			return GlobalBomIndexConfiguration.get()
					.getSourceById(uuid);
		}

		@Override
		public BomSource getSourceByName(final Run<?, ?> run, final String name) {
			return GlobalBomIndexConfiguration.get()
					.getSourceByName(name);
		}

		@Override
		public List<BomSource> getSources(final Item item) {
			return GlobalBomIndexConfiguration.get()
					.getSources();
		}
	}

	public static final class IdRef extends RefUUID {

		private static final long serialVersionUID = 1L;

		@DataBoundConstructor
		public IdRef() {
			super();
		}

		@Extension
		public static final class Descriptor extends RefUUID.Descriptor {

			public ListBoxModel doFillUuidItems(@AncestorInPath final Item item, @QueryParameter final String uuid) {
				return IdentifierUtils.allValuesAsListBox(BomSourceProvider.allSources(item), uuid, BomSource::getName,
						false);
			}
		}
	}
}
