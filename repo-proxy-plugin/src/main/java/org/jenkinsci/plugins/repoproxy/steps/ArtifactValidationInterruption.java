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
package org.jenkinsci.plugins.repoproxy.steps;

import java.util.Objects;

import jenkins.model.CauseOfInterruption;

public class ArtifactValidationInterruption extends CauseOfInterruption {

	private static final long serialVersionUID = 1L;

	protected final String message;

	protected String packageUrl = null;

	public ArtifactValidationInterruption(final String message) {
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

	public String getPURL() {
		return this.packageUrl;
	}

	@Override
	public String getShortDescription() {
		final StringBuilder msg = new StringBuilder(this.message);
		if (this.packageUrl != null)
			msg.append("\n  PURL: " + this.packageUrl);
		return msg.toString();

	}

	public ArtifactValidationInterruption setPURL(final String purl) {
		this.packageUrl = purl;
		return this;
	}

	@Override
	public int hashCode() {
		return this.message == null ? 0 : this.message.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof ArtifactValidationInterruption other) {
			return Objects.equals(this.getMessage(), other.getMessage()) //
					&& Objects.equals(this.getPURL(), other.getPURL());
		}
		return false;
	}
}
