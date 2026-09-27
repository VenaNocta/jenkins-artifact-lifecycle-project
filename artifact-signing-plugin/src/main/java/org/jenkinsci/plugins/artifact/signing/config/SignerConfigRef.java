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
package org.jenkinsci.plugins.artifact.signing.config;

import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.artifact.lifecycle.config.RefUUID;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Item;
import hudson.util.ListBoxModel;

public class SignerConfigRef extends RefUUID {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public SignerConfigRef() {
		super();
	}

	@Extension
	public static class Descriptor extends RefUUID.Descriptor {

		public ListBoxModel doFillUuidItems(@AncestorInPath final Item item, @QueryParameter final String uuid) {
			return IdentifierUtils.allValuesAsListBox(ArtifactSignerIndex.get()
					.getSigners(item), uuid, SignerConfig::getName, true);
		}
	}
}