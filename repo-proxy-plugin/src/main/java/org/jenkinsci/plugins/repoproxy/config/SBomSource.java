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

import java.io.Serializable;
import java.util.UUID;

import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Describable;

public abstract class SBomSource implements Describable<SBomSource>, Serializable {

	private static final long serialVersionUID = 1L;

	public static final String JSON_ID_FIELD_NAME = "id";

	@NonNull
	protected final UUID id;

	protected String name;
	protected String description;

	public SBomSource(final UUID id, final String name) {
		this(id, name, null);
	}

	public SBomSource(final UUID id, final String name, final String description) {
		this.id = id == null ? UUID.randomUUID() : id;
		this.name = Util.fixEmptyAndTrim(name);
		this.description = Util.fixEmptyAndTrim(description);
	}

	public UUID getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	@DataBoundSetter
	public void setName(@NonNull final String name) {
		this.name = Util.fixEmptyAndTrim(name);
	}

	@DataBoundSetter
	public void setDescription(@NonNull final String description) {
		this.description = Util.fixEmptyAndTrim(description);
	}

	@Override
	public SBomSourceDescriptor getDescriptor() {
		return (SBomSourceDescriptor) Describable.super.getDescriptor();
	}
}
