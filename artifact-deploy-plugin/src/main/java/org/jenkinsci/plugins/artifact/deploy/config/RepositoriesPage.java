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
package org.jenkinsci.plugins.artifact.deploy.config;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactLifecycleConfigLink;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactLifecycleConfigPage;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

@Extension
public class RepositoriesPage extends ArtifactLifecycleConfigPage {

	@Override
	public String getUrlName() {
		return "repos";
	}

	@Override
	public String getDisplayName() {
		return Messages.RepositoriesPage_DisplayName();
	}

	@Extension(ordinal = 8_000L)
	public static class ReposLink extends ArtifactLifecycleConfigLink {

		@Override
		@NonNull
		public String getUrl() {
			return ACTION_URL + "/repos";
		}

		@Override
		@NonNull
		public String getIcon() {
			return "icon-gear2 icon-md";
		}

		@Override
		public String getDisplayName() {
			return Messages.ArtifactLifecycleCnfLink_ReposLink_DisplayName();
		}
	}

}
