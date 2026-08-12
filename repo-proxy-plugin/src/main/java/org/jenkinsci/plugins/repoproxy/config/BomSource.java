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

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;

import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Run;
import hudson.model.TaskListener;

public abstract class BomSource implements Describable<BomSource>, Serializable {

	private static final long serialVersionUID = 1L;

	public static final String JSON_ID_FIELD_NAME = "uuid";

	@NonNull
	protected final UUID uuid;

	protected String name;
	protected String description;

	public BomSource(final UUID uuid, final String name) {
		this(uuid, name, null);
	}

	public BomSource(final UUID uuid, final String name, final String description) {
		this.uuid = uuid == null ? UUID.randomUUID() : uuid;
		this.name = Util.fixEmptyAndTrim(name);
		this.description = Util.fixEmptyAndTrim(description);
	}

	public UUID getUuid() {
		return this.uuid;
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

	public abstract BomSourceCache buildCache(final Run<?, ?> run, final Launcher nodeBasedLauncher,
			final FilePath cachePath, final TaskListener listener) throws IOException, InterruptedException;

	@Override
	public BomSourceDescriptor getDescriptor() {
		return (BomSourceDescriptor) Describable.super.getDescriptor();
	}
}
