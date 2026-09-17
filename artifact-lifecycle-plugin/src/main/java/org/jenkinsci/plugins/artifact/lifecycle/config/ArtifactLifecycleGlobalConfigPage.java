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
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

public abstract class ArtifactLifecycleGlobalConfigPage<T extends Describable<T>> extends ArtifactLifecycleConfigPage
		implements Describable<T> {

	protected abstract Logger getLogger();

	// used by jelly
	public abstract Descriptor<GlobalConfiguration> getConfigDescriptor();

	@RequirePOST
	@NonNull
	@Restricted(NoExternalUse.class)
	// used by jelly
	public synchronized HttpResponse doConfigure(@NonNull final StaplerRequest2 req)
			throws IOException, ServletException, FormException {
		boolean result = configure(req, req.getSubmittedForm());
		getLogger().log(Level.FINE, getClass().getSimpleName() + " saved: " + result);
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
}
