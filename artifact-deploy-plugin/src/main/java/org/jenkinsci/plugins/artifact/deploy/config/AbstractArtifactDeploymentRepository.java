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

import org.jenkinsci.plugins.artifact.deploy.config.api.ArtifactDeploymentRepository;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class AbstractArtifactDeploymentRepository implements ArtifactDeploymentRepository {

	private static final long serialVersionUID = 1L;

	protected String id;

	protected AbstractArtifactDeploymentRepository(@NonNull final String id) {
		this.id = id;
	}

	public String getId() {
		return this.id;
	}

	@DataBoundSetter
	public void setId(@NonNull String id) {
		this.id = id;
	}

	@Override
	public Helper getHelper() {
		final Descriptor<ArtifactDeploymentRepository> d = Jenkins.get()
				.getDescriptor(getClass());
		if (d instanceof Helper)
			return (Helper) d;
		if (this instanceof Helper)
			return (Helper) this;
		return null;
	}

	/**
	 * By default looks for a nested class (conventionally named
	 * {@code DescriptorImpl}) implementing {@link Descriptor} and marked with
	 * {@link Extension}.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public Descriptor<ArtifactDeploymentRepository> getDescriptor() {
		return Jenkins.get()
				.getDescriptorOrDie(getClass());
	}

}
