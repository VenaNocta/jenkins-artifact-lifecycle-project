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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jenkins.util.VirtualFile;

public class Bundle implements Serializable {

	private static final long serialVersionUID = 1L;

	protected final Map<VirtualFile, Map<String, Serializable>> artifactMap = new ConcurrentHashMap<>();
	protected final String id;

	public Bundle(final String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public Stream<Entry<VirtualFile, Map<String, Serializable>>> getArtifactStream() {
		return this.artifactMap.entrySet()
				.stream()
				.map(UnmodifiableEntry::new);
	}

	public Iterator<Map.Entry<VirtualFile, Map<String, Serializable>>> getArtifactIterator() {
		return getArtifactStream().iterator();
	}

	public Bundle indexArtifact(@NonNull final VirtualFile artifact,
			@Nullable final Map<String, Serializable> metadata) {
		Objects.requireNonNull(artifact, "Artifact must not be Null!");
		final Map<String, Serializable> map = this.artifactMap.computeIfAbsent(artifact,
				k -> new ConcurrentHashMap<>());
		if (metadata != null)
			map.putAll(metadata);
		return this;
	}

	public boolean isEmpty() {
		return this.artifactMap.isEmpty();
	}

	@Override
	public int hashCode() {
		return this.id.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof Bundle other)
			return this.artifactMap.equals(other.artifactMap);
		return false;
	}

	public Bundle copy() {
		final Bundle bundle = new Bundle(this.id);
		for (Entry<VirtualFile, Map<String, Serializable>> entry : this.artifactMap.entrySet())
			bundle.artifactMap.put(entry.getKey(), entry.getValue());
		return bundle;
	}

	public Bundle merge(@NonNull final Bundle bundle) {
		Objects.requireNonNull(bundle);
		for (Entry<VirtualFile, Map<String, Serializable>> entry : bundle.artifactMap.entrySet()) {
			this.artifactMap.merge(entry.getKey(), entry.getValue(), (base, dom) -> {
				base.putAll(dom);
				return base;
			});
		}
		return this;
	}

	public static Predicate<? super Entry<VirtualFile, Map<String, Serializable>>> wrapUnsafeFilter(
			@NonNull Predicate<? super Entry<VirtualFile, Map<String, Serializable>>> filter) {
		Objects.requireNonNull(filter);
		return e -> filter.test(new UnmodifiableEntry(e));
	}

	private static class UnmodifiableEntry implements Map.Entry<VirtualFile, Map<String, Serializable>> {
		private final Map.Entry<VirtualFile, Map<String, Serializable>> e;
		private final Map<String, Serializable> value;

		UnmodifiableEntry(Map.Entry<VirtualFile, Map<String, Serializable>> e) {
			this.e = Objects.requireNonNull(e);
			final Map<String, Serializable> value = this.e.getValue();
			if (value == null)
				this.value = null;
			else
				this.value = Collections.unmodifiableMap(value);
		}

		public VirtualFile getKey() {
			return this.e.getKey();
		}

		public Map<String, Serializable> getValue() {
			return this.value;
		}

		public Map<String, Serializable> setValue(Map<String, Serializable> value) {
			throw new UnsupportedOperationException();
		}

		public int hashCode() {
			return e.hashCode();
		}

		public boolean equals(Object o) {
			if (this == o)
				return true;
			return o instanceof Map.Entry<?, ?> t && eq(e.getKey(), t.getKey()) && eq(e.getValue(), t.getValue());
		}

		public String toString() {
			return e.toString();
		}

		private static boolean eq(Object o1, Object o2) {
			return o1 == null ? o2 == null : o1.equals(o2);
		}
	}
}
