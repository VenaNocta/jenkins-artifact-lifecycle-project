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

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Describable;

public abstract class ArtifactRepository implements Describable<ArtifactRepository>, IdentifiedByUUID, Serializable {

	private static final long serialVersionUID = 1L;

	protected final Set<ArtifactRepositoryTag> tags = new TreeSet<>();

	@NonNull
	protected final UUID uuid;

	// consider tracking the owner(item)!
	// final Item item=null;
	// => if privs/creds => must be available with owner!

	protected String name;
	protected String description;

	public ArtifactRepository(final UUID uuid, final String name) {
		this(uuid, name, null);
	}

	public ArtifactRepository(final UUID uuid, final String name, final String description) {
		this.uuid = IdentifierUtils.fixNull(uuid);
		this.name = Util.fixEmptyAndTrim(name);
		this.description = Util.fixEmptyAndTrim(description);
	}

	@Override
	public UUID getUuid() {
		return this.uuid;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public Set<ArtifactRepositoryTag> getTags() {
		return new HashSet<>(this.tags);
	}

	public String getTagsList() {
		return getTags().parallelStream()
				.map(ArtifactRepositoryTag::getDisplayName)
				.collect(Collectors.joining(" "));
	}

	public boolean matchTags(final Predicate<ArtifactRepositoryTag> check) {
		if (check == null)
			return false;
		return this.tags.parallelStream()
				.anyMatch(check);
	}

	@DataBoundSetter
	public void setName(@NonNull final String name) {
		this.name = Util.fixEmptyAndTrim(name);
	}

	@DataBoundSetter
	public void setDescription(@NonNull final String description) {
		this.description = Util.fixEmptyAndTrim(description);
	}

	@DataBoundSetter
	public void setTags(@NonNull final Collection<ArtifactRepositoryTag> tags) {
		this.tags.clear();
		if (tags != null)
			this.tags.addAll(tags);
	}

	@Override
	public ArtifactRepositoryDescriptor getDescriptor() {
		return (ArtifactRepositoryDescriptor) Describable.super.getDescriptor();
	}
}
