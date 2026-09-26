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
package org.jenkinsci.plugins.artifact.signing.config;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class NamedSignerConfigRef extends SignerConfigRef {

	private static final long serialVersionUID = 1L;

	public static final Comparator<NamedSignerConfigRef> COMPARE_BY_NAME = new NameComparator();

	protected boolean locked = false;
	protected String name;

	@DataBoundConstructor
	public NamedSignerConfigRef() {
		super();
	}

	public String getName() {
		return this.name;
	}

	public boolean isLocked() {
		return this.locked;
	}

	public boolean isUnlocked() {
		return !this.locked;
	}

	@DataBoundSetter
	public void setName(final String name) {
		this.name = Util.fixEmptyAndTrim(name);
	}

	@DataBoundSetter
	public void setLocked(final boolean locked) {
		this.locked = locked;
	}

	@Override
	public boolean equals(final Object obj) {
		return this == obj || obj instanceof NamedSignerConfigRef other
				&& (this.name == null ? other.name == null : this.name.equals(other.name) && super.equals(obj));
	}

	public static Predicate<NamedSignerConfigRef> distinctByName() {
		final Set<String> names = ConcurrentHashMap.newKeySet();
		return ref -> {
			final String name = ref.getName();
			return name == null ? false : names.add(name);
		};
	}

	@Extension
	public static class Descriptor extends SignerConfigRef.Descriptor {

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckName(@AncestorInPath Item item, @QueryParameter String value) {
			return Util.fixEmptyAndTrim(value) == null ? FormValidation.error("Name is required") : FormValidation.ok();
		}

		@Override
		public ListBoxModel doFillUuidItems(@AncestorInPath final Item item, @QueryParameter final String uuid) {
			return IdentifierUtils.allValuesAsListBox(ArtifactSignerIndex.get()
					.getSigners(item), uuid, SignerConfig::getName, true);
		}
	}

	public static class NameComparator implements Comparator<NamedSignerConfigRef>, Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final NamedSignerConfigRef o1, final NamedSignerConfigRef o2) {
			if (o1 == null)
				return o2 == null ? 0 : -1;
			if (o2 == null)
				return 1;
			return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
		}
	}
}
