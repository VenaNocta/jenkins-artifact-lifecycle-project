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

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class ArtifactDeploymentRepository
		implements Describable<ArtifactDeploymentRepository>, Serializable, ExtensionPoint {

	private static final long serialVersionUID = 1L;

	protected String id;
	protected String description;

	protected ArtifactDeploymentRepository(@NonNull final String id) {
		this(id, null);
	}

	protected ArtifactDeploymentRepository(@NonNull final String id, @CheckForNull final String description) {
		this.id = id;
		this.description = description;
	}

	public String getId() {
		return this.id;
	}

	public String getDescription() {
		return this.description;
	}

	@DataBoundSetter
	public void setId(String id) {
		this.id = id;
	}

	@DataBoundSetter
	public void setDescription(@NonNull String description) {
		this.description = description;
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
