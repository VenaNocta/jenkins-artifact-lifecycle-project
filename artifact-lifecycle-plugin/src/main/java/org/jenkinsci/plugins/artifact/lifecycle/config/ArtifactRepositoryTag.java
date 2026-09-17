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

import java.util.Objects;

import org.jenkinsci.plugins.artifact.lifecycle.config.tags.Messages;
import org.jvnet.localizer.ResourceBundleHolder;

public class ArtifactRepositoryTag {

	/**
	 * The resource bundle reference
	 *
	 */
	private final static ResourceBundleHolder holder = ResourceBundleHolder.get(Messages.class);

	protected final String key;
	protected final String type;
	protected final String refKey;

	public ArtifactRepositoryTag(final String key) {
		this(key, null, null);
	}

	public ArtifactRepositoryTag(final String key, final String refKey) {
		this(key, null, refKey);
	}

	public ArtifactRepositoryTag(final String key, final String type, final String refKey) {
		Objects.nonNull(key);
		this.key = key;
		this.type = type;
		this.refKey = refKey;
	}

	public String getDisplayName() {
		if (this.refKey == null)
			return this.key;
		final String name = holder.format(this.refKey);
		if (name == null)
			return this.key;
		return name;
	}

	public final String getKey() {
		return this.key;
	}

	public final String getType() {
		return this.type;
	}

	@Override
	public int hashCode() {
		return this.key.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof ArtifactRepositoryTag other) {
			return Objects.equals(this.key, other.getKey()) && Objects.equals(this.type, other.getType());
		}
		return false;
	}
}
