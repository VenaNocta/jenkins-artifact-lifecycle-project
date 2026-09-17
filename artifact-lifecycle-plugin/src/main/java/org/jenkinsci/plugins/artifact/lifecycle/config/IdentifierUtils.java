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
package org.jenkinsci.plugins.artifact.lifecycle.config;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.Map.Entry;

import org.apache.commons.collections.map.HashedMap;
import org.kohsuke.stapler.StaplerRequest2;

import hudson.Util;
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class IdentifierUtils {

	public static UUID fixNull(final UUID uuid) {
		return uuid == null ? UUID.randomUUID() : uuid;
	}

	public static <T extends IdentifiedByUUID> void updateMapEntries(final Map<UUID, T> baseData,
			final Map<UUID, T> data) {
		// retain & update old "known" entries
		for (Iterator<Entry<UUID, T>> i = baseData.entrySet()
				.iterator(); i.hasNext();) {
			final Entry<UUID, T> entry = i.next();
			final UUID id = entry.getKey();
			final T newData = data.remove(id);
			if (newData == null) {
				// value got deleted
				i.remove();
				continue;
			}
			entry.setValue(newData);
		}
		baseData.putAll(data);
	}

	public static JSONArray getOptJsonAsArray(final JSONObject json, final String key) {
		JSONArray jsonArray = json.optJSONArray(key);
		if (jsonArray == null) {
			jsonArray = new JSONArray();
			JSONObject jsonObj = json.optJSONObject(key);
			if (jsonObj != null)
				jsonArray.add(jsonObj);
		}
		return jsonArray;
	}

	public static <T extends IdentifiedByUUID> Map<UUID, T> bindIdentifiedEntries(final StaplerRequest2 req,
			final JSONArray jsonArray, final Class<T> type, final Predicate<UUID> isIdDuplicate,
			final BiFunction<UUID, T, FormException> postBindCheck, final String jsonIdFieldName) throws FormException {
		final Map<UUID, T> newMap = new HashedMap(jsonArray.size());
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.size(); i++) {
				final JSONObject dataObj = jsonArray.getJSONObject(i);
				final String parsedId = Util.fixEmpty(dataObj.optString(jsonIdFieldName));
				UUID uuid = null;
				if (parsedId != null) {
					// try parse, invalid equals null value
					try {
						uuid = UUID.fromString(parsedId);
					} catch (IllegalArgumentException ignored) {
					}
				}
				if (uuid == null) {
					// generate unique entry.id in context
					do {
						uuid = UUID.randomUUID();
					} while (isIdDuplicate.test(uuid) || newMap.containsKey(uuid));
					dataObj.element(jsonIdFieldName, uuid.toString());
				} else {
					// check for duplicate ids
					if (newMap.containsKey(uuid))
						continue;
				}

				final T data = req.bindJSON(type, dataObj);
				final FormException ex = postBindCheck.apply(uuid, data);
				if (ex != null)
					throw ex;
				newMap.put(data.getUuid(), data);
			}
		}
		return newMap;
	}
}
