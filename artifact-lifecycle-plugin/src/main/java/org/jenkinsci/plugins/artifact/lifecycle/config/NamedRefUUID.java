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
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Util;
import hudson.model.Item;
import hudson.util.FormValidation;

public class NamedRefUUID extends RefUUID {

	private static final long serialVersionUID = 1L;

	public static final String REGEX_NAME = "[a-zA-Z0-9_\\.\\-<>#]+";
	public static final String REGEX_NAME_OR_COMMENT = "^(?:[a-zA-Z0-9_.\\-<>]+|#[a-zA-Z0-9_.\\-<> ]+)$";

	public static final Comparator<NamedRefUUID> COMPARE_BY_NAME = new NameComparator();
	public static final Comparator<NamedRefUUID> COMPARE_BY_NAME_WITH_COMMENTS = new NameComparatorWithComments();

	protected boolean readonly = false;
	protected String name;

	@DataBoundConstructor
	public NamedRefUUID() {
		super();
	}

	public String getName() {
		return this.name;
	}

	public boolean isReadonly() {
		return this.readonly;
	}

	public boolean isWritable() {
		return !this.readonly;
	}

	@DataBoundSetter
	public void setName(final String name) {
		this.name = Util.fixEmptyAndTrim(name);
	}

	@DataBoundSetter
	public void setReadonly(final boolean readonly) {
		this.readonly = readonly;
	}

	protected <T extends NamedRefUUID> T copyTo(final T ref) {
		ref.setReadonly(this.readonly);
		ref.setName(this.name);
		ref.setUuidValue(this.uuidValue);
		return ref;
	}

	@Override
	public boolean equals(final Object obj) {
		return this == obj || obj instanceof NamedRefUUID other && (//
		(this.name == null ? other.name == null : this.name.equals(other.name)) //
				&& other.readonly == this.readonly //
				&& super.equals(obj));
	}

	public static <T extends NamedRefUUID> T asReadonly(T ref) {
		if ((ref = copy(ref)) == null)
			return null;
		ref.setReadonly(true);
		return ref;
	}

	public static <T extends NamedRefUUID> T copy(final T ref) {
		if (ref == null)
			return null;
		try {
			return ref.copyTo((T) ref.getDescriptor()
					.getKlass()
					.toJavaClass()
					.getDeclaredConstructor()
					.newInstance());
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			return null;
		}
	}

	public static Predicate<NamedRefUUID> distinctByName() {
		final Set<String> names = ConcurrentHashMap.newKeySet();
		return ref -> {
			final String name = ref.getName();
			return name == null ? false : names.add(name);
		};
	}

	public static boolean isComment(final String name) {
		return name == null ? false : name.charAt(0) == '#';
	}

	public static String unComment(String name) {
		if (name == null)
			return null;
		name = StringUtils.stripStart(name, "#");
		name = StringUtils.stripStart(name, null);
		return name;
	}

	public static boolean noComment(final NamedRefUUID ref) {
		final String name = ref.getName();
		return name == null ? false : !isComment(name);
	}

	public static abstract class Descriptor extends RefUUID.Descriptor {

		protected boolean allowCommentAsName() {
			return false;
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckName(@AncestorInPath Item item, @QueryParameter String value) {
			if ((value = Util.fixEmptyAndTrim(value)) == null)
				return FormValidation.error("Name is required");
			if (!allowCommentAsName() && isComment(value))
				return FormValidation.error("Comments are not allowed as Names!");
			if (!value.matches(NamedRefUUID.REGEX_NAME_OR_COMMENT))
				return FormValidation.error("Invalid characters");
			return FormValidation.ok();
		}
	}

	public static class NameComparator implements Comparator<NamedRefUUID>, Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final NamedRefUUID o1, final NamedRefUUID o2) {
			if (o1 == null)
				return o2 == null ? 0 : -1;
			if (o2 == null)
				return 1;
			return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
		}
	}

	public static class NameComparatorWithComments implements Comparator<NamedRefUUID>, Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final NamedRefUUID o1, final NamedRefUUID o2) {
			if (o1 == null)
				return o2 == null ? 0 : -1;
			if (o2 == null)
				return 1;
			return String.CASE_INSENSITIVE_ORDER.compare(unComment(o1.name), unComment(o2.name));
		}
	}
}
