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

import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.DomainSpecificationDescriptor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

public class SigningKeySpecification extends DomainSpecification {

	private static final long serialVersionUID = 1L;

	private final boolean canSign;

	@DataBoundConstructor
	public SigningKeySpecification(final boolean canSign) {
		this.canSign = canSign;
	}

	public boolean getCanSign() {
		return this.canSign;
	}

	@Override
	@NonNull
	public Result test(@NonNull final DomainRequirement scope) {
		if (scope instanceof SigningKeyRequirement req) {
			if (getCanSign() == req.canSign())
				return Result.POSITIVE;
			return Result.NEGATIVE;
		}
		return Result.UNKNOWN;
	}

	@Extension
	public static class SigningKeySpecificationDescriptor extends DomainSpecificationDescriptor {
		@Override
		public String getDisplayName() {
			return Messages.SigningKeySpecification_Desc_DisplayName();
		}
	}

}
