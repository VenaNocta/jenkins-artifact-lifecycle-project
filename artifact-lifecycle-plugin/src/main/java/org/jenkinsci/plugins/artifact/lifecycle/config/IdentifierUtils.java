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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Map.Entry;

import org.kohsuke.stapler.StaplerRequest2;

import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class IdentifierUtils {

	public static final String ERR_POST_BINDING_UUID_MISMATCH = "post binding UUID mismatch";

	public static final String SELECT_NONE_TEXT = "- none -";

	public static UUID fixNull(final UUID uuid) {
		return uuid == null ? UUID.randomUUID() : uuid;
	}

	public static UUID fixInvalidUUID(String uuid) {
		if ((uuid = Util.fixEmptyAndTrim(uuid)) == null)
			return null;
		try {
			return UUID.fromString(uuid);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static String nullInvalidUUID(String uuid) {
		if ((uuid = Util.fixEmptyAndTrim(uuid)) == null)
			return null;
		try {
			UUID.fromString(uuid);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
		return uuid;
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
			final JSONObject json, final Class<T> type, final Predicate<UUID> isIdDuplicate, final String key,
			final String jsonIdFieldName, final BiFunction<UUID, T, FormException> postBindCheck) throws FormException {
		final JSONArray jsonArray = getOptJsonAsArray(json, key);
		final Map<UUID, T> newMap = new LinkedHashMap<>(jsonArray.size());
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
				if (!uuid.equals(data.getUuid()))
					throw new FormException(ERR_POST_BINDING_UUID_MISMATCH, key);
				if (postBindCheck != null) {
					final FormException ex = postBindCheck.apply(uuid, data);
					if (ex != null)
						throw ex;
				}
				newMap.put(data.getUuid(), data);
			}
		}
		return newMap;
	}

	public static <T extends IdentifiedByUUID> ListBoxModel allValuesAsListBox(final List<T> values, String selectedId,
			final Function<T, String> nameSupplier, final boolean withNone) {
		return allValuesAsListBox(values, selectedId, nameSupplier, withNone, SELECT_NONE_TEXT);
	}

	public static <T extends IdentifiedByUUID> ListBoxModel allValuesAsListBox(final List<T> values, String selectedId,
			final Function<T, String> nameSupplier, final boolean withNone, final String noneText) {
		selectedId = IdentifierUtils.nullInvalidUUID(selectedId);
		final ListBoxModel model = new ListBoxModel();

		boolean hasValue = false;
		for (T value : values) {
			final UUID uuid = value.getUuid();
			if (uuid == null)
				continue;
			final String val = uuid.toString();
			if (selectedId != null && selectedId.equals(val))
				hasValue = true;
			String name = Util.fixEmptyAndTrim(nameSupplier.apply(value));
			name = String.format("%s » %s", val, name == null ? "???" : name);
			model.add(name, val);
		}
		if (withNone)
			model.add(0, new Option(noneText, null));
		if (!hasValue) {
			if (selectedId == null) {
				if (!withNone)
					model.add(0, new Option(noneText, selectedId));
			} else
				model.add(0, new Option(String.format("%s » ???", selectedId), selectedId));
		}
		return model;
	}
}
