/*
 * Copyright © 2025 VenaNocta (venanocta@gmail.com)
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
package org.jenkinsci.plugins.artifact.deploy.config;

import java.util.List;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.DescriptorByNameOwner;
import hudson.model.RootAction;

@Extension
public class ArtifactLifecycle implements RootAction, DescriptorByNameOwner {

	public static final String ACTION_URL = "artifact-lifecycle";

	@Override
	public String getUrlName() {
		return ACTION_URL;
	}

	@Override
	public String getIconFileName() {
		return "icon-gear2 icon-md";
	}

	@Override
	public String getDisplayName() {
		return "-- ArtifactLifecycle --";
	}

	/**
	 * Used to generate the list of links on the sidepanel.
	 */
	@NonNull
	public List<ArtifactLifecycleLink> getArtifactLifecycleLinks() {
		return ExtensionList.lookup(ArtifactLifecycleLink.class);
	}

}
