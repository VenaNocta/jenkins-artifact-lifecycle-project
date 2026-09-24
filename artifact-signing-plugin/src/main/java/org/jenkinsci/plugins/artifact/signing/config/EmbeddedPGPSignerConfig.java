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

import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.signing.engines.PGPSigningEngine;
import org.jenkinsci.plugins.artifact.signing.engines.SigningEngine;
import org.jenkinsci.plugins.pgpcredentials.PGPRequirementBuilder;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class EmbeddedPGPSignerConfig extends ASignerConfig {

	private static final long serialVersionUID = 1L;

	@CheckForNull
	private String credentialId;

	@DataBoundConstructor
	public EmbeddedPGPSignerConfig(@NonNull final String uuid, final String name, @CheckForNull String description,
			@CheckForNull String credentialId) {
		super(UUID.fromString(uuid), name, description);
		this.credentialId = Util.fixEmptyAndTrim(credentialId);
	}

	public String getCredentialId() {
		return this.credentialId;
	}

	@DataBoundSetter
	public void setCredentialId(@CheckForNull final String credentialId) {
		this.credentialId = Util.fixEmptyAndTrim(credentialId);
	}

	@Override
	public boolean isValid(final Item item) {
		return getCredentialsById(item, this.credentialId) != null;
	}

	@Override
	public SigningEngine createEngine(final StepContext context, final String signaturePrefix,
			final String signatureSuffix) {
		return new PGPSigningEngine(context, signaturePrefix, signatureSuffix, this.credentialId);
	}

	public static PGPPrivateKeyCredentials getCredentialsById(final Item item, final String id) {
		if (id == null)
			return null;
		return CredentialsMatchers.firstOrNull( //
				CredentialsProvider.lookupCredentialsInItem( //
						PGPPrivateKeyCredentials.class, item, null),
				CredentialsMatchers.withId(id));
	}

	@OptionalExtension(requirePlugins = { "pgp-credentials" })
	public static class Descriptor extends SignerConfigDescriptor {

		@Override
		public String getDisplayName() {
			return Messages.EmbeddedPGPSignerConfig_DisplayName();
		}

		public ListBoxModel doFillCredentialIdItems(@AncestorInPath final Item item) {
			if (item == null && !Jenkins.get()
					.hasPermission(Jenkins.ADMINISTER) || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
				return new StandardListBoxModel();
			}
			return new StandardListBoxModel().includeEmptyValue()
					.includeAs(ACL.SYSTEM2, item, PGPPrivateKeyCredentials.class, PGPRequirementBuilder.create()
							.requireSigningCapability()
							.build());
		}

		// TODO check if this key is correct - validate with the owner Item!
		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckCredentialId(@AncestorInPath final Item item, @QueryParameter String value) {
			if ((value = StringUtils.trimToNull(value)) == null)
				return FormValidation.error("PGP-Private Key is required");

			if (EmbeddedPGPSignerConfig.getCredentialsById(item, value) == null)
				return FormValidation.error("Defined PGP-Private Key is unavailable!");

			return FormValidation.ok();
		}
	}
}
