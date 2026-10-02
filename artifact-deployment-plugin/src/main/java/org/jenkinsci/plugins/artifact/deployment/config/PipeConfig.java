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
package org.jenkinsci.plugins.artifact.deployment.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifiedByUUID;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.artifact.lifecycle.config.ReferenceableByName;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.util.FormValidation;

public class PipeConfig
		implements Describable<PipeConfig>, ReferenceableByName<NamedPipeRef>, IdentifiedByUUID, Serializable {

	private static final long serialVersionUID = 1L;

	@NonNull
	protected final UUID uuid;

	// consider tracking the owner(item)!
	// final Item item=null;
	// => if privs/creds => must be available with owner!

	protected String name;
	protected String description;

	protected List<DeploymentTask> tasks = null;

	@DataBoundConstructor
	public PipeConfig(@NonNull final String uuid, final String name, @CheckForNull String description) {
		this(IdentifierUtils.fixInvalidUUID(uuid), name, description);
	}

	public PipeConfig(final UUID uuid, final String name, final String description) {
		this.uuid = IdentifierUtils.fixNull(uuid);
		this.name = Util.fixEmptyAndTrim(name);
		this.description = Util.fixEmptyAndTrim(description);
	}

	@Override
	public UUID getUuid() {
		return this.uuid;
	}

	@Override
	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public List<DeploymentTask> getTasks() {
		if (this.tasks == null)
			return new ArrayList<>(0);
		return new ArrayList<DeploymentTask>(this.tasks);
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
	public void setTasks(@NonNull final Collection<DeploymentTask> tasks) {
		this.tasks = tasks == null ? null : new LinkedList<>(tasks);
	}

	@Override
	public NamedPipeRef toNamedRef() {
		final NamedPipeRef ref = new NamedPipeRef();
		ref.setReadonly(true);
		ref.setUuidValue(getUuid());
		ref.setName(getName());
		return ref;
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<PipeConfig> {

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckName(@QueryParameter String value, @QueryParameter String currentUuid) {
			value = StringUtils.trimToNull(value);
			// name is optional anyway
			if (value == null)
				return FormValidation.ok();
			// check invalid chars
			if (!value.matches("[a-zA-Z0-9_\\.\\-<>#]+"))
				return FormValidation.error("Invalid characters");
			// check duplicates
			return FormValidation.ok();
		}
	}
}
