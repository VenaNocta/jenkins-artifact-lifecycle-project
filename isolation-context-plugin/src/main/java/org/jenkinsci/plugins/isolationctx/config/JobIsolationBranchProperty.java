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
package org.jenkinsci.plugins.isolationctx.config;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.JobDecorator;

public class JobIsolationBranchProperty extends BranchProperty {

	private final JobIsolationProperty property;

	@DataBoundConstructor
	public JobIsolationBranchProperty(final boolean enabled, final boolean unsafeCheckoutEnabled) {
		this.property = new JobIsolationProperty(enabled, unsafeCheckoutEnabled);
	}

	public boolean isEnabled() {
		return this.property.isEnabled();
	}

	public boolean isUnsafeCheckoutEnabled() {
		return this.property.isUnsafeCheckoutEnabled();
	}

	public JobIsolationProperty getJobProperty() {
		return this.property;
	}

	@Override
	public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(final Class<P> clazz) {
		return new JobDecorator<P, B>() {
			@Override
			public List<JobProperty<? super P>> jobProperties(final List<JobProperty<? super P>> properties) {
				final List<JobProperty<? super P>> result = new ArrayList<>(properties);
				result.add(JobIsolationBranchProperty.this.getJobProperty());
				return result;
			}
		};
	}

	@OptionalExtension(requirePlugins = "branch-api")
	public static class Descriptor extends BranchPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return "Context Isolation Defaults";
		}
	}
}