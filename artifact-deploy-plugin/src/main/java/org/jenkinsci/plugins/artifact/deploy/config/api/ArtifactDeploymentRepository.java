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
package org.jenkinsci.plugins.artifact.deploy.config.api;

import java.io.Serializable;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Describable;

public interface ArtifactDeploymentRepository
		extends Describable<ArtifactDeploymentRepository>, Serializable, ExtensionPoint {

	@NonNull
	public String getId();

	public void setId(@NonNull String id);

	public Helper getHelper();

	public interface Helper {

		@NonNull
		public String randomId();

	}

}
