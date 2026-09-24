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
package org.jenkinsci.plugins.artifact.signing.ui;

import java.util.logging.Logger;

import org.jenkinsci.plugins.artifact.lifecycle.ui.ArtifactLifecycleConfigLink;
import org.jenkinsci.plugins.artifact.lifecycle.ui.ArtifactLifecycleGlobalConfigPage;
import org.jenkinsci.plugins.artifact.signing.config.ArtifactSignerIndex;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

@Extension
public class ArtifactSignersPage extends ArtifactLifecycleGlobalConfigPage<ArtifactSignersPage> {

	public static final String PAGE_KEY = "signer";

	private static final Logger LOGGER = Logger.getLogger(ArtifactSignersPage.class.getName());

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}

	@Override
	public String getUrlName() {
		return PAGE_KEY;
	}

	@Override
	public String getDisplayName() {
		return Messages.ArtifactSignersPage_DisplayName();
	}

	// used by jelly
	public Descriptor<GlobalConfiguration> getConfigDescriptor() {
		return Jenkins.get()
				.getDescriptorOrDie(ArtifactSignerIndex.class);
	}

	/**
	 * Our {@link Descriptor}.
	 */
	@Extension
	public static final class DescriptorImpl extends Descriptor<ArtifactSignersPage> {
		/**
		 * {@inheritDoc}
		 */
		@NonNull
		@Override
		public String getDisplayName() {
			return Messages.ArtifactSignersPage_DisplayName();
		}
	}

	@Extension(ordinal = 6_000L)
	public static class ArtifactSignersLink extends ArtifactLifecycleConfigLink {

		@Override
		@NonNull
		public String getUrl() {
			return ACTION_URL + "/" + PAGE_KEY;
		}

		@Override
		@NonNull
		public String getIcon() {
			return "icon-gear2 icon-md";
		}

		@Override
		public String getDisplayName() {
			return Messages.ArtifactLifecycleCnfLink_ArtifactSignersLink_DisplayName();
		}
	}
}
