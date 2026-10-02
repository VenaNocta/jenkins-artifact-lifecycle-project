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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ItemGroup;

public abstract class AGlobalNameResolver<T extends IdentifiedByUUID & ReferenceableByName<R>, R extends NamedRefUUID> {

	protected abstract ExtensionList<? extends NameResolver<R>> allResolvers();

	protected UUID getDefaultEntryId() {
		return null;
	}

	public abstract T getEntryById(final UUID uuid);

	public abstract Stream<T> getEntriesAsStream(Item context);

	public T getEntryByName(@Nullable final Item item, @Nullable final String name) {
		final String name0 = Util.fixEmptyAndTrim(name);
		final Optional<UUID> selected = allResolvers().stream()
				.map(resolver -> resolver.resolveName(item, name0))
				.findFirst()
				.orElse(resolveNameLocally(item, name0));
		return selected.isEmpty() ? null : getEntryById(selected.get());
	}

	public T findEntryByName(@Nullable final Item item, @Nullable final String name) {
		final String name0 = Util.fixEmptyAndTrim(name);
		final ExtensionList<? extends NameResolver<R>> resolvers = allResolvers();

		Object itemOrGroup = item;
		Optional<UUID> selected = null;
		while (itemOrGroup != null) {
			if (itemOrGroup instanceof Item item0) {
				itemOrGroup = item0.getParent();
				selected = resolvers.stream()
						.map(resolver -> resolver.resolveName(item0, name0))
						.filter(Objects::nonNull)
						.findFirst()
						.orElse(null);
				if (selected != null)
					break;
			} else
				break;
		}
		if (selected == null)
			selected = resolveNameLocally(item, name0);
		return selected.isEmpty() ? null : getEntryById(selected.get());
	}

	public Optional<UUID> resolveNameLocally(@Nullable final Item item, final String name) {
		if (name == null)
			return Optional.ofNullable(getDefaultEntryId());
		return getEntriesAsStream(item)//
				.filter(entry -> {
					final String name0 = entry.getName();
					if (name0 == null || name0.length() == 0)
						return false;
					return !NamedRefUUID.isComment(name0) && name.equals(name0);
				})
				.map(IdentifiedByUUID::getUuid)
				.findFirst();
	}

	public Set<R> findAllEntryNameMappings(@Nullable ItemGroup<?> itemGroup) {
		final List<? extends NameResolver<R>> resolvers = allResolvers();
		final Set<R> set = new LinkedHashSet<>();

		boolean inherit = true;
		while (inherit) {
			if (itemGroup instanceof Item item) {
				itemGroup = item.getParent();
				for (NameResolver<R> resolver : resolvers) {
					inherit = inherit & resolver.collectNameMappings(item, set);
				}
			} else
				break;
		}
		if (inherit) {
			getEntriesAsStream(itemGroup instanceof Item item ? item : null) //
					.map(ReferenceableByName::toNamedRef)
					// remove mappings for hidden entries
					.filter(NamedRefUUID::noComment)
					.forEach(set::add);
		}
		return set;
	}
}
