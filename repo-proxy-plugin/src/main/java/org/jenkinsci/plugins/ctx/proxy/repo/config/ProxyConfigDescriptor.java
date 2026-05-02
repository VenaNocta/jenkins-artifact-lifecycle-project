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
package org.jenkinsci.plugins.ctx.proxy.repo.config;

import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public abstract class ProxyConfigDescriptor extends Descriptor<ProxyConfig> {

	@NonNull
	public String randomId() {
		return UUID.randomUUID()
				.toString();
	}

	@Restricted(NoExternalUse.class)
	@POST
	public FormValidation doCheckId(@QueryParameter String value, @QueryParameter String currentId) {
		value = StringUtils.trimToNull(value);
		// generated id
		if (value == null)
			return FormValidation.ok("ID will be generated!");
		// unchanged
		if (value.equals(StringUtils.trimToEmpty(currentId)))
			return FormValidation.ok();
		// check invalid chars
		if (!value.matches("[a-zA-Z0-9_\\.-<>]+"))
			return FormValidation.error("Invalid characters");
		// check duplicates
		if (RepoProxyConfiguration.get()
				.getConfig(value) != null)
			return FormValidation.error("ID already in use!");
		return FormValidation.ok();
	}
}
