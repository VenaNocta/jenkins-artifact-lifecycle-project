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

import static org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactLifecycleConfigPage.ACTION_URL;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;

@Extension
public class GlobalArtifactLifecycleConfigLink extends ManagementLink
		implements Describable<GlobalArtifactLifecycleConfigLink> {

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

	/**
	 * Used to generate the list of links on the sidepanel.
	 */
	@NonNull
	public List<ArtifactLifecycleConfigLink> getNavLinks() {
		return ArtifactLifecycleConfigLink.all();
	}

	protected String getCurrentRequestToken() {
		final StaplerRequest2 req = Stapler.getCurrentRequest2();
		String token = req.getRequestURI();
		if (token.isBlank())
			return null;
		final String thisPath = req.getContextPath() + "/manage/" + ACTION_URL + '/';
		if (token.startsWith(thisPath))
			token = token.substring(thisPath.length());
		if (token.endsWith("/"))
			token = token.substring(0, token.length() - 1);
		return token;
	}

	// delegate sub-URLs to registered subpages
	public Object getDynamic(String token) {
		token = getCurrentRequestToken();
		final Map<String, ArtifactLifecycleConfigPage> map = ArtifactLifecycleConfigPage.all()
				.stream()
				.collect(Collectors.toMap(ArtifactLifecycleConfigPage::getUrlName, Function.identity()));
		if (map.containsKey(token)) {
			return this;
		}
		if (!"GET".equals(Stapler.getCurrentRequest2()
				.getMethod())) {
			final int endIdx = StringUtils.lastIndexOf(token, '/');
			if (endIdx == -1)
				return null;
			return map.get(token.substring(0, endIdx));
		}
		return null;
	}

	public ArtifactLifecycleConfigPage getPage() {
		final String token = getCurrentRequestToken();
		for (ArtifactLifecycleConfigPage page : ArtifactLifecycleConfigPage.all()) {
			if (page.getUrlName()
					.equals(token)) {
				return page;
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Descriptor<GlobalArtifactLifecycleConfigLink> getDescriptor() {
		return Jenkins.get()
				.getDescriptorOrDie(getClass());
	}

	/**
	 * Our {@link Descriptor}.
	 */
	@Extension
	public static final class DescriptorImpl extends Descriptor<GlobalArtifactLifecycleConfigLink> {
		/**
		 * {@inheritDoc}
		 */
		@NonNull
		@Override
		public String getDisplayName() {
			return Messages.GlobalArtifactLifecycleCnfLink_DisplayName();
		}
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
