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
package org.jenkinsci.plugins.repoproxy.maven.config;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import net.runeduniverse.lib.repo.maven.api.BasicCredentials;

public class DefaultBasicRepoCredentials implements BasicCredentials {

	protected final StandardUsernamePasswordCredentials credentials;

	public DefaultBasicRepoCredentials(final StandardUsernamePasswordCredentials credentials) {
		this.credentials = credentials;
	}

	@Override
	public String getUser() {
		return this.credentials.getUsername();
	}

	@Override
	public String getPassword() {
		return this.credentials.getPassword()
				.getPlainText();
	}
}
