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
package org.jenkinsci.plugins.repoproxy.config;

import java.util.UUID;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class ScmSBomSource extends SBomSource {

	private static final long serialVersionUID = 1L;

	public ScmSBomSource(final UUID id, final String name) {
		super(id, name);
	}

	@DataBoundConstructor
	public ScmSBomSource(final String id, final String name) {
		super(UUID.fromString(id), name);
	}

	@Extension
	public static class Descriptor extends SBomSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "SCM SBom Repository";
		}
	}
}
