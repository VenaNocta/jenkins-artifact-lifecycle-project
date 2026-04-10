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
package org.jenkinsci.plugins.pgpcredentials;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jenkinsci.plugins.pgpcredentials.api.PGPSigningKeyRequirement;
import org.jenkinsci.plugins.pgpcredentials.api.PGPValidKeyRequirement;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import edu.umd.cs.findbugs.annotations.NonNull;

public class PGPRequirementBuilder {
	/**
	 * The list of requirements.
	 */
	@NonNull
	protected final List<DomainRequirement> requirements;

	protected PGPRequirementBuilder(final @NonNull List<DomainRequirement> requirements) {
		this.requirements = requirements;
	}

	public PGPRequirementBuilder requireValid() {
		return requireValid(true);
	}

	public PGPRequirementBuilder requireInvalid() {
		return requireValid(false);
	}

	public PGPRequirementBuilder requireValid(final boolean valid) {
		for (Iterator<?> i = requirements.iterator(); i.hasNext();) {
			if (i.next() instanceof PGPValidKeyRequirement r)
				if (r.valid() != valid)
					i.remove();
		}
		this.requirements.add(new ValidKeyRequirement(valid));
		return this;
	}

	public PGPRequirementBuilder ignoreValidity() {
		for (Iterator<?> i = requirements.iterator(); i.hasNext();) {
			if (i.next() instanceof PGPValidKeyRequirement)
				i.remove();
		}
		return this;
	}

	public PGPRequirementBuilder requireSigningCapability() {
		return requireSigningCapability(true);
	}

	public PGPRequirementBuilder requireSigningCapability(final boolean value) {
		for (Iterator<?> i = requirements.iterator(); i.hasNext();) {
			if (i.next() instanceof PGPSigningKeyRequirement r)
				if (r.canSign() != value)
					i.remove();
		}
		this.requirements.add(new SigningKeyRequirement(value));
		return this;
	}

	public PGPRequirementBuilder ignoreSigningCapability() {
		for (Iterator<?> i = requirements.iterator(); i.hasNext();) {
			if (i.next() instanceof PGPSigningKeyRequirement)
				i.remove();
		}
		return this;
	}

	public List<DomainRequirement> build() {
		return new ArrayList<DomainRequirement>(this.requirements);
	}

	public static PGPRequirementBuilder create() {
		return new PGPRequirementBuilder(new LinkedList<>());
	}

}
