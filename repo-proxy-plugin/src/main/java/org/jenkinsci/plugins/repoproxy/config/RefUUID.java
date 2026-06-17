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

import java.util.UUID;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

public class RefUUID implements Describable<RefUUID> {

	protected UUID uuidValue = null;

	@DataBoundConstructor
	public RefUUID() {
	}

	public UUID getUuidValue() {
		return this.uuidValue;
	}

	public String getUuidStrValue() {
		return this.uuidValue.toString();
	}

	@DataBoundSetter
	public void setUuidValue(final UUID value) {
		this.uuidValue = value;
	}

	@DataBoundSetter
	public void setUuidStrValue(String value) {
		try {
			value = Util.fixEmptyAndTrim(value);
			this.uuidValue = value == null ? null : UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			this.uuidValue = null;
		}
	}

	public boolean isValid() {
		return this.uuidValue != null;
	}

	public static boolean checkValid(final RefUUID ref) {
		return ref != null && ref.isValid();
	}

	@Override
	public int hashCode() {
		return this.uuidValue == null ? 0 : this.uuidValue.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		return this == obj || obj instanceof RefUUID other
				&& (this.uuidValue == null ? other.uuidValue == null : this.uuidValue.equals(other.uuidValue));
	}

	public static abstract class DescriptorRefUUID extends Descriptor<RefUUID> {

		public abstract ListBoxModel allValuesAsListBox(final Item item);

		public ListBoxModel doFillUuidStrValueItems(@AncestorInPath final Item item,
				@QueryParameter final String uuidStrValue) {
			final ListBoxModel model = allValuesAsListBox(item);

			boolean hasValue = false;
			for (Option option : model) {
				if (uuidStrValue != null && uuidStrValue.equals(option.value))
					hasValue = true;
				final String name = Util.fixEmptyAndTrim(option.name);
				option.name = String.format("%s » %s", option.value, name == null ? "???" : name);
			}
			if (!hasValue) {
				if (Util.fixEmptyAndTrim(uuidStrValue) == null || "---".equals(uuidStrValue))
					model.add(0, new Option("- none -", uuidStrValue));
				else
					model.add(0, new Option(String.format("%s » ???", uuidStrValue), uuidStrValue));
			}
			return model;
		}
	}
}
