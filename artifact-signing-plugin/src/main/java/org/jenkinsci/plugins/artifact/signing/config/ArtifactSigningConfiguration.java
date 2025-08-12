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
package org.jenkinsci.plugins.artifact.signing.config;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pgpcredentials.PGPRequirementBuilder;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;

@Extension
@Symbol("globalArtifactSigningConfig")
public class ArtifactSigningConfiguration extends GlobalConfiguration {

	public static final String DEFAULT_SECURE_LABEL_EXPRESSION = "secure";
	public static final String DEFAULT_SECURE_WORKSPACE_SUFFIX = "secure";

	@CheckForNull
	private String defaultCredentialsId;

	@GuardedBy("this")
	private String secureLabelExpression = DEFAULT_SECURE_LABEL_EXPRESSION;

	@GuardedBy("this")
	private String secureWorkspaceSuffix = DEFAULT_SECURE_WORKSPACE_SUFFIX;

	public ArtifactSigningConfiguration() {
		load();
	}

	public String getDefaultCredentialsId() {
		return Util.fixEmpty(this.defaultCredentialsId);
	}

	public synchronized String getSecureLabelExpression() {
		if (StringUtils.isBlank(this.secureLabelExpression))
			setSecureLabelExpression(null);
		return this.secureLabelExpression;
	}

	public synchronized String getSecureWorkspaceSuffix() {
		if (StringUtils.isBlank(this.secureWorkspaceSuffix))
			setSecureWorkspaceSuffix(null);
		return this.secureLabelExpression;
	}

	@DataBoundSetter
	public void setDefaultCredentialsId(@CheckForNull final String defaultCredentialsId) {
		this.defaultCredentialsId = defaultCredentialsId;
		save();
	}

	@DataBoundSetter
	public synchronized void setSecureLabelExpression(String value) {
		if ((value = StringUtils.trimToNull(value)) == null)
			value = DEFAULT_SECURE_LABEL_EXPRESSION;
		this.secureLabelExpression = value;
		save();
	}

	@DataBoundSetter
	public synchronized void setSecureWorkspaceSuffix(String value) {
		if ((value = StringUtils.trimToNull(value)) == null)
			value = DEFAULT_SECURE_WORKSPACE_SUFFIX;
		this.secureWorkspaceSuffix = value;
		save();
	}

	public ListBoxModel doFillDefaultCredentialsIdItems(@AncestorInPath Item item) {
		if (item == null && !Jenkins.get()
				.hasPermission(Jenkins.ADMINISTER) || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
			return new StandardListBoxModel();
		}
		return new StandardListBoxModel().includeEmptyValue()
				.includeAs(ACL.SYSTEM2, item, PGPPrivateKeyCredentials.class, PGPRequirementBuilder.create()
						.requireSigningCapability()
						.build());
	}

	@NonNull
	public static ArtifactSigningConfiguration get() {
		return GlobalConfiguration.all()
				.getInstance(ArtifactSigningConfiguration.class);
	}

	// provides the global fallback values
	@Extension(ordinal = -10000)
	public static final class GlobalArtifactSigningConfigurationProvider
			implements ArtifactSigningConfigurationProvider {

		@Inject
		private ArtifactSigningConfiguration cnf;

		@Override
		public String getDefaultCredentialsId(@Nullable final Run<?, ?> run) {
			return this.cnf.getDefaultCredentialsId();
		}

	}

}
