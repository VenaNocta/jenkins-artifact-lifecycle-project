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
package org.jenkinsci.plugins.repoproxy.maven.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.repoproxy.config.RefUUID;
import org.jenkinsci.plugins.repoproxy.config.SBomSourceProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CyclonedxValidatorConfig extends ValidatorConfig {

	private static final long serialVersionUID = 1L;

	protected Set<IdRef> sourceIds = new LinkedHashSet<>();

	@DataBoundConstructor
	public CyclonedxValidatorConfig() {
	}

	public List<IdRef> getSourceIds() {
		if (this.sourceIds == null)
			return new ArrayList<>();
		return new ArrayList<>(this.sourceIds);
	}

	@DataBoundSetter
	public void setSourceIds(final Collection<IdRef> col) {
		if (col == null || col.isEmpty())
			this.sourceIds.clear();
		else {
			this.sourceIds = new LinkedHashSet<>();
			col.stream()
					.filter(RefUUID::checkValid)
					.forEach(this.sourceIds::add);
		}
	}

	@Override
	public Set<UUID> getRequiredSBoms() {
		return this.sourceIds.stream()
				.map(RefUUID::getUuidValue)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean isValid() {
		return !this.sourceIds.isEmpty();
	}

	@Extension
	public static class Descriptor extends ValidatorConfigDescriptor {
	}

	public static final class IdRef extends RefUUID {

		protected String filterPath = null;

		@DataBoundConstructor
		public IdRef() {
			super();
		}

		public String getFilterPath() {
			return this.filterPath;
		}

		@DataBoundSetter
		public void setFilterPath(final String filterPath) {
			this.filterPath = Util.fixEmptyAndTrim(filterPath);
		}

		@Override
		public boolean isValid() {
			return super.isValid()
					&& (this.filterPath == null || !Descriptor.PROHIBITED_DOUBLE_DOT.matcher(this.filterPath)
							.matches());
		}

		@Override
		public boolean equals(final Object obj) {
			return this == obj || obj instanceof IdRef other //
					&& StringUtils.compare(this.filterPath, other.filterPath, true) == 0 //
					&& super.equals(obj);
		}

		@Extension
		public static final class Descriptor extends DescriptorRefUUID {

			/**
			 * Matches ".." in positions where it would be treated as the parent directory.
			 *
			 * <p>
			 * Used to prevent {@link #filterPath} from being used for directory traversal.
			 */
			protected static final Pattern PROHIBITED_DOUBLE_DOT = Pattern.compile("(^|.*[\\\\/])\\.\\.($|[\\\\/].*)");

			@Override
			public ListBoxModel allValuesAsListBox(final Item item) {
				return SBomSourceProvider.allSourcesAsListBox(item);
			}

			@Restricted(NoExternalUse.class)
			@POST
			public FormValidation doCheckFilterPath(@QueryParameter final String value) {
				if (value == null)
					return FormValidation.ok();
				if (PROHIBITED_DOUBLE_DOT.matcher(value)
						.matches())
					return FormValidation.error("Filter path may not contain '..'");
				return FormValidation.ok();
			}
		}
	}
}
