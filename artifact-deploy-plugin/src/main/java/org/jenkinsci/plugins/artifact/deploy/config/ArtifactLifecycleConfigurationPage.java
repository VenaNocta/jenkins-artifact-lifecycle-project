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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

public abstract class ArtifactLifecycleConfigurationPage
		extends AbstractDescribableImpl<ArtifactLifecycleConfigurationPage> implements ExtensionPoint {

	public abstract String getUrlName();

	/**
	 * Used to generate the list of links on the sidepanel.
	 */
	@NonNull
	public List<ArtifactLifecycleConfigurationLink> getNavLinks() {
		return ExtensionList.lookup(ArtifactLifecycleConfigurationLink.class);
	}

}
