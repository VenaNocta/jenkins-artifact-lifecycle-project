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

import java.util.List;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;

public abstract class ArtifactLifecycleConfigPage implements ExtensionPoint, Action {

	public static final String ACTION_URL = "artifact-lifecycle";

	public abstract String getUrlName();

	public abstract String getDisplayName();

	@Override
	public String getIconFileName() {
		return null;
	}

	/**
	 * Used to generate the list of links on the sidepanel.
	 */
	@NonNull
	public List<ArtifactLifecycleConfigLink> getNavLinks() {
		return ArtifactLifecycleConfigLink.all();
	}

	// delegate sub-URLs to registered subpages
	public Object getDynamic(final String token) {
		if (token == null)
			return null;
		return ArtifactLifecycleConfigPage.all()
				.stream()
				.filter(page -> token.equals(page.getUrlName()))
				.findFirst()
				.orElse(null);
	}

	/**
	 * All registered instances.
	 */
	@NonNull
	public static ExtensionList<ArtifactLifecycleConfigPage> all() {
		return ExtensionList.lookup(ArtifactLifecycleConfigPage.class);
	}
}
