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
package org.jenkinsci.plugins.artifact.lifecycle.ui;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Job;

public abstract class ArtifactLifecycleConfigLink implements ExtensionPoint {

	private static final Logger LOGGER = Logger.getLogger(ArtifactLifecycleConfigLink.class.getName());

	/**
	 * Get the URL this link should point to, which will be used by
	 * {@link #getDisplayUrl()}. If this is not absolute, {@link #getDisplayUrl()}
	 * will link to this within the current context.
	 */
	@NonNull
	public abstract String getUrl();

	/**
	 * Get the actual URL to use in sidepanel.jelly. If {@link #getUrl()} is not
	 * absolute, this will try to get the current Job context and return a url
	 * starting with that job's {@link Job#getUrl()} appended with
	 * {@link #getUrl()}.
	 */
	@NonNull
	public final String getDisplayUrl() {
		final String url = getUrl();

		try {
			if (new URI(url).isAbsolute()) {
				return url;
			}
		} catch (URISyntaxException e) {
			LOGGER.log(Level.WARNING, "Failed to parse URL for " + url, e);
			return "";
		}

		final StaplerRequest2 req = Stapler.getCurrentRequest2();
		if (req == null) {
			return url;
		}

		final StringBuilder toAppend = new StringBuilder();
		{
			// prepend context path
			final String contextPath = req.getContextPath();
			toAppend.append(contextPath);

			if (!contextPath.endsWith("/")) {
				toAppend.append("/");
			}
		}

		// prepend item path
		final Item item = req.findAncestorObject(Item.class);
		if (item == null) {
			toAppend.append("manage/");
		} else {
			final String itemUrl = item.getUrl();
			toAppend.append(item);

			if (!itemUrl.endsWith("/")) {
				toAppend.append("/");
			}
		}

		toAppend.append(url);

		return toAppend.toString();
	}

	/**
	 * Get the icon information for the link.
	 */
	@NonNull
	public String getIcon() {
		return "icon-help icon-md";
	}

	/**
	 * Get the display name for the link.
	 */
	@NonNull
	public abstract String getDisplayName();

	/**
	 * Check whether the link should target a new window - this defaults to false;
	 */
	public boolean inNewWindow() {
		return false;
	}

	/**
	 * All registered instances.
	 */
	@NonNull
	public static ExtensionList<ArtifactLifecycleConfigLink> all() {
		return ExtensionList.lookup(ArtifactLifecycleConfigLink.class);
	}

}
