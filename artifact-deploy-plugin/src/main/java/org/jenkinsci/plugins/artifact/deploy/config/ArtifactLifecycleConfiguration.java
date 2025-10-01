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
import java.util.logging.Logger;

import org.jenkinsci.Symbol;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import jenkins.model.GlobalConfigurationCategory;

@Extension
public class ArtifactLifecycleConfiguration extends ManagementLink {

	private static final Logger LOGGER = Logger.getLogger(ArtifactLifecycleConfiguration.class.getName());

	public static final String ACTION_URL = "artifact-lifecycle";

	@Override
	public String getUrlName() {
		return ACTION_URL;
	}

	@Override
	public Category getCategory() {
		return Category.CONFIGURATION;
	}

	@Override
	public String getIconFileName() {
		return "icon-gear2 icon-md";
	}

	@Override
	public String getDisplayName() {
		return Messages.ArtifactLifecycleCnf_DisplayName();
	}

	@Override
	public String getDescription() {
		return Messages.ArtifactLifecycleCnf_Description();
	}

	/**
	 * Used to generate the list of links on the sidepanel.
	 */
	@NonNull
	public List<ArtifactLifecycleConfigurationLink> getNavLinks() {
		return ExtensionList.lookup(ArtifactLifecycleConfigurationLink.class);
	}

	// delegate sub-URLs to registered subpages
	public ArtifactLifecycleConfigurationPage getDynamic(final String token) {
		for (ArtifactLifecycleConfigurationPage page : ExtensionList.lookup(ArtifactLifecycleConfigurationPage.class)) {
			if (page.getUrlName()
					.equals(token)) {
				return page;
			}
		}
		return null;
	}

	@Extension
	@Symbol("globalArtifactLifecycleConfiguration")
	public static class ArtifactLifecycleConfigurationCategory extends GlobalConfigurationCategory {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getDisplayName() {
			return Messages.ArtifactLifecycleCnf_DisplayName();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getShortDescription() {
			return Messages.ArtifactLifecycleCnf_Description();
		}
	}

	@Extension(ordinal = 10_000L)
	public static class OverviewLink extends ArtifactLifecycleConfigurationLink {
		@Override
		@NonNull
		public String getUrl() {
			return ACTION_URL;
		}

		@Override
		public String getDisplayName() {
			return Messages.ArtifactLifecycleCnfLink_OverviewLink_DisplayName();
		}
	}
}
