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
package org.jenkinsci.plugins.pgpcredentials;

import org.jenkinsci.plugins.pgpcredentials.api.PGPValidKeyRequirement;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;

public class ValidKeyRequirement extends DomainRequirement implements PGPValidKeyRequirement {

	private static final long serialVersionUID = 1L;

	private final boolean valid;

	public ValidKeyRequirement() {
		this(true);
	}

	public ValidKeyRequirement(final boolean valid) {
		this.valid = valid;
	}

	public boolean valid() {
		return this.valid;
	}

}
