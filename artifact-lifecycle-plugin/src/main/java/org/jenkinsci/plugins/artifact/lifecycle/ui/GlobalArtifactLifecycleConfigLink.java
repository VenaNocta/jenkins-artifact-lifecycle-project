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

import static org.jenkinsci.plugins.artifact.lifecycle.ui.ArtifactLifecycleConfigPage.ACTION_URL;

import java.util.function.Predicate;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.Messages;
import org.kohsuke.stapler.StaplerProxy;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import jenkins.model.GlobalConfigurationCategory;

@Extension
public class GlobalArtifactLifecycleConfigLink extends ManagementLink implements StaplerProxy {

	@SuppressWarnings("rawtypes")
	public static final Predicate<Descriptor> FILTER = d -> d.getCategory() instanceof GlobalConfigurationCategory;

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
		return Messages.GlobalArtifactLifecycleCnfLink_DisplayName();
	}

	@Override
	public String getDescription() {
		return Messages.GlobalArtifactLifecycleCnfLink_Description();
	}

	@Override
	public Object getTarget() {
		return ExtensionList.lookupFirst(ArtifactLifecycleConfigPage.class);
	}

	@Extension
	@Symbol("globalArtifactLifecycleConfiguration")
	public static class ArtifactLifecycleCategory extends GlobalConfigurationCategory {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getDisplayName() {
			return Messages.GlobalArtifactLifecycleCnfLink_DisplayName();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getShortDescription() {
			return Messages.GlobalArtifactLifecycleCnfLink_Description();
		}
	}
}
