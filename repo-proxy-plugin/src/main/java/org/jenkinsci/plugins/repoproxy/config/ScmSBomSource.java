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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Item;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;

public class ScmSBomSource extends SBomSource {

	private static final long serialVersionUID = 1L;

	protected final String version;
	protected final SCMSource scm;

	public ScmSBomSource(final UUID id, final String name, final String version, final SCMSource scm) {
		super(id, name);
		this.version = Util.fixEmptyAndTrim(version);
		this.scm = scm;
	}

	@DataBoundConstructor
	public ScmSBomSource(final String id, final String name, final String version, final SCMSource scm) {
		super(UUID.fromString(id), name);
		this.version = Util.fixEmptyAndTrim(version);
		this.scm = scm;
	}

	@Extension
	public static class Descriptor extends SBomSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "SCM SBom Repository";
		}

		/**
		 * Returns only implementations overriding
		 * {@link SCMSource#retrieve(String, TaskListener)} or
		 * {@link SCMSource#retrieve(String, TaskListener, Item)}.
		 */
		@Restricted(NoExternalUse.class) // Jelly, Hider
		public Collection<SCMSourceDescriptor> getSCMDescriptors() {
			final List<SCMSourceDescriptor> descriptors = new ArrayList<>();
			for (SCMSourceDescriptor d : ExtensionList.lookup(SCMSourceDescriptor.class)) {
				if (Util.isOverridden(SCMSource.class, d.clazz, "retrieve", String.class, TaskListener.class)
						|| Util.isOverridden(SCMSource.class, d.clazz, "retrieve", String.class, TaskListener.class,
								Item.class)) {
					descriptors.add(d);
				}
			}
			return descriptors;
		}
	}
}
