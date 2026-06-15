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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalSBomConfig")
public class SBomConfiguration extends GlobalConfiguration {

	protected final Map<UUID, SBomSource> sources = new LinkedHashMap<>();

	public SBomConfiguration() {
		load();
	}

	public SBomSource getSourceById(final UUID id) {
		return this.sources.get(id);
	}

	public SBomSource getSourceByName(final String name) {
		if (name == null)
			return null;
		return this.sources.values()
				.stream()
				.filter(s -> name.equals(s.getName()))
				.findFirst()
				.orElse(null);
	}

	// used by jelly
	public List<SBomSource> getSources() {
		return new ArrayList<SBomSource>(this.sources.values());
	}

	public void setSourcesMap(final Map<UUID, SBomSource> sources) {
		// TODO consider using a read-write-lock
		// retain & update old "known" entries
		for (Iterator<Entry<UUID, SBomSource>> i = this.sources.entrySet()
				.iterator(); i.hasNext();) {
			final Entry<UUID, SBomSource> entry = i.next();
			final UUID id = entry.getKey();
			final SBomSource newSource = sources.remove(id);
			if (newSource == null) {
				// value got deleted
				i.remove();
				continue;
			}
			entry.setValue(newSource);
		}
		this.sources.putAll(sources);
	}

	@Override
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		JSONArray sourcesArray = json.optJSONArray("sources");
		if (sourcesArray == null) {
			sourcesArray = new JSONArray();
			JSONObject jsonObj = json.optJSONObject("sources");
			if (jsonObj != null)
				sourcesArray.add(jsonObj);
		}

		final Map<UUID, SBomSource> newSourceMap = new LinkedHashMap<>();

		if (sourcesArray != null) {
			for (int i = 0; i < sourcesArray.size(); i++) {
				final JSONObject sourceObj = sourcesArray.getJSONObject(i);
				final String parsedId = Util.fixEmpty(sourceObj.optString(SBomSource.JSON_ID_FIELD_NAME));
				UUID id = null;
				if (parsedId != null) {
					// try parse, invalid equals null value
					try {
						id = UUID.fromString(parsedId);
					} catch (IllegalArgumentException ignored) {
					}
				}
				if (id == null) {
					// generate unique entry.id in context
					do {
						id = UUID.randomUUID();
					} while (this.sources.containsKey(id) || newSourceMap.containsKey(id));
					sourceObj.element(SBomSource.JSON_ID_FIELD_NAME, id.toString());
				} else {
					// check for duplicate ids
					if (newSourceMap.containsKey(id))
						continue;
				}

				final SBomSource source = req.bindJSON(SBomSource.class, sourceObj);
				if (!id.equals(source.id)) {
					throw new FormException("UUID mismatch after binding", "sources");
				}
				newSourceMap.put(source.id, source);
			}
		}
		this.setSourcesMap(newSourceMap);
		save();
		return true;
	}

	@NonNull
	public static SBomConfiguration get() {
		return GlobalConfiguration.all()
				.getInstance(SBomConfiguration.class);
	}

	@Extension(ordinal = -10000)
	public static final class GlobalSBomSourceProvider implements SBomSourceProvider {

		@Override
		public SBomSource getSourceById(final Run<?, ?> run, final UUID id) {
			return SBomConfiguration.get()
					.getSourceById(id);
		}

		@Override
		public SBomSource getSourceByName(final Run<?, ?> run, final String name) {
			return SBomConfiguration.get()
					.getSourceByName(name);
		}

		@Override
		public List<SBomSource> getSources(final Item item) {
			return SBomConfiguration.get()
					.getSources();
		}
	}

	public static final class IdRef extends RefUUID {

		@DataBoundConstructor
		public IdRef() {
			super();
		}

		@Extension
		public static final class Descriptor extends DescriptorRefUUID {

			@Override
			public ListBoxModel allValuesAsListBox(final Item item) {
				return SBomSourceProvider.allSourcesAsListBox(item);
			}
		}
	}
}
