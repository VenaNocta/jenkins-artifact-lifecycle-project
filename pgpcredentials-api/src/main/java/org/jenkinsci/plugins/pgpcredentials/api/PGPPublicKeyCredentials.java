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
package org.jenkinsci.plugins.pgpcredentials.api;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.bouncycastle.openpgp.PGPPublicKey;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainRestrictedCredentials;

public interface PGPPublicKeyCredentials extends StandardCredentials, DomainRestrictedCredentials {

	public boolean isValid() throws IOException, InterruptedException;

	public PGPPublicKey getPublicKey() throws IOException, InterruptedException;

	public boolean isMasterKey() throws IOException, InterruptedException;

	public byte[] getPublicKeyFingerprint() throws IOException, InterruptedException;

	public Iterator<String> getUserIds() throws IOException, InterruptedException;

	public default boolean matches(final List<DomainRequirement> domainRequirements) {
		for (DomainRequirement req : domainRequirements) {
			if (match(req))
				continue;
			return false;
		}
		return true;
	}

	public default boolean match(final DomainRequirement domainRequirement) {
		if (domainRequirement instanceof PGPValidKeyRequirement) {
			final PGPValidKeyRequirement req = (PGPValidKeyRequirement) domainRequirement;
			try {
				return isValid() == req.valid();
			} catch (IOException | InterruptedException e) {
				return false;
			}
		}
		return true;
	}
}
