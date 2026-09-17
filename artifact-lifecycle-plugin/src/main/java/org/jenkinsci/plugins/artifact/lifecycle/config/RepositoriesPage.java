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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

@Extension
public class RepositoriesPage extends ArtifactLifecycleConfigPage implements Describable<RepositoriesPage> {

	public static final String PAGE_KEY = "repo";

	private static final Logger LOGGER = Logger.getLogger(ArtifactLifecycleConfigPage.class.getName());

	@Override
	public String getUrlName() {
		return PAGE_KEY;
	}

	@Override
	public String getDisplayName() {
		return Messages.RepositoriesPage_DisplayName();
	}

	// used by jelly
	public Descriptor<GlobalConfiguration> getConfigDescriptor() {
		return Jenkins.get()
				.getDescriptorOrDie(GlobalRepositoryIndex.class);
	}

	@RequirePOST
	@NonNull
	@Restricted(NoExternalUse.class)
	// used by jelly
	public synchronized HttpResponse doConfigure(@NonNull final StaplerRequest2 req)
			throws IOException, ServletException, FormException {
		boolean result = configure(req, req.getSubmittedForm());
		LOGGER.log(Level.FINE, "RepositoriesPage saved: " + result);
		return FormApply.success(req.getContextPath() + "/manage/" + ACTION_URL + '/' + getUrlName());
	}

	// used by jelly
	protected boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		final Descriptor<GlobalConfiguration> desc = getConfigDescriptor();
		final String sectionName = desc.getJsonSafeClassName();
		if (json.has(sectionName)) {
			desc.configure(req, json.getJSONObject(sectionName));
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Descriptor<RepositoriesPage> getDescriptor() {
		return Jenkins.get()
				.getDescriptorOrDie(getClass());
	}

	/**
	 * Our {@link Descriptor}.
	 */
	@Extension
	public static final class DescriptorImpl extends Descriptor<RepositoriesPage> {
		/**
		 * {@inheritDoc}
		 */
		@NonNull
		@Override
		public String getDisplayName() {
			return Messages.RepositoriesPage_DisplayName();
		}
	}

	@Extension(ordinal = 8_000L)
	public static class ReposLink extends ArtifactLifecycleConfigLink {

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
			return Messages.ArtifactLifecycleCnfLink_ReposLink_DisplayName();
		}
	}
}
