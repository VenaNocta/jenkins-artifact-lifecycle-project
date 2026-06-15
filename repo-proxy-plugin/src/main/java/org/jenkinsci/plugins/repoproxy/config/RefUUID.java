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

	protected UUID value = null;

	@DataBoundConstructor
	public RefUUID() {
	}

	public UUID getValue() {
		return this.value;
	}

	public String getStrValue() {
		return this.value.toString();
	}

	@DataBoundSetter
	public void setValue(final UUID value) {
		this.value = value;
	}

	@DataBoundSetter
	public void setStrValue(final String value) {
		try {
			this.value = UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			this.value = null;
		}
	}

	public static abstract class DescriptorRefUUID extends Descriptor<RefUUID> {

		public abstract ListBoxModel allValuesAsListBox(final Item item);

		public ListBoxModel doFillStrValueItems(@AncestorInPath final Item item, @QueryParameter String strValue) {
			final ListBoxModel model = allValuesAsListBox(item);

			boolean hasValue = false;
			for (Option option : model) {
				if (strValue != null && strValue.equals(option.value))
					hasValue = true;
				final String name = Util.fixEmptyAndTrim(option.name);
				option.name = String.format("%s » %s", option.value, name == null ? "???" : name);
			}
			if (!hasValue) {
				if (Util.fixEmptyAndTrim(strValue) == null || "---".equals(strValue))
					model.add(0, new Option("- none -", strValue));
				else
					model.add(0, new Option(String.format("%s » ???", strValue), strValue));
			}
			return model;
		}
	}
}
