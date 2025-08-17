/*
 * Copyright © 2025 VenaNocta (venanocta@gmail.com)
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
package org.jenkinsci.plugins.artifact.deploy.context;

import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;

public class BundleContext implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_BUNDLE_ID = "default";

	private final Map<String, Bundle> bundles = new ConcurrentHashMap<>();

	public Bundle getBundle() {
		return getBundle(DEFAULT_BUNDLE_ID);
	}

	public Bundle getBundle(@NonNull String id) {
		if ((id = StringUtils.trimToNull(id)) == null)
			return null;
		return this.bundles.computeIfAbsent(id, Bundle::new);
	}

	public boolean hasBundle(final String id) {
		return this.bundles.containsKey(StringUtils.trimToNull(id));
	}

	public Bundle putBundle(@NonNull final Bundle bundle) {
		Objects.requireNonNull(bundle);
		final String id = bundle.getId();
		Objects.requireNonNull(id, "Bundle ID must be defined!");
		return this.bundles.put(id, bundle);
	}

	public void merge(@NonNull final BundleContext other) {
		Objects.requireNonNull(other);
		for (Entry<String, Bundle> entry : other.bundles.entrySet())
			this.bundles.merge(entry.getKey(), entry.getValue(), Bundle::merge);
	}

}
