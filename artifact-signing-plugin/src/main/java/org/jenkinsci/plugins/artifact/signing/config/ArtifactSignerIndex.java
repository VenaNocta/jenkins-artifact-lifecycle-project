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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifiedByUUID;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.artifact.lifecycle.ui.GlobalArtifactLifecycleConfigLink.ArtifactLifecycleCategory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalArtifactSignerIndex")
public class ArtifactSignerIndex extends GlobalConfiguration {

	public static final String DEFAULT_SECURE_LABEL_EXPRESSION = "secure";
	public static final String DEFAULT_SECURE_WORKSPACE_SUFFIX = "secure";

	protected transient final Map<UUID, SignerConfig> signerMap = new LinkedHashMap<>();

	private Collection<SignerConfig> signers = new LinkedList<>();

	@CheckForNull
	private UUID defaultSignerId;

	@GuardedBy("this")
	private String secureLabelExpression = DEFAULT_SECURE_LABEL_EXPRESSION;

	@GuardedBy("this")
	private String secureWorkspaceSuffix = DEFAULT_SECURE_WORKSPACE_SUFFIX;

	public ArtifactSignerIndex() {
		load();
	}

	@Override
	public synchronized void load() {
		super.load();
		this.signerMap.clear();
		this.signers.stream()
				.forEachOrdered(t -> this.signerMap.put(t.getUuid(), t));
		// fix data post load
		this.secureLabelExpression = fixSecureLabelExpression(this.secureLabelExpression);
		this.secureWorkspaceSuffix = fixSecureWorkspaceSuffix(this.secureWorkspaceSuffix);
	}

	@CheckForNull
	public UUID getDefaultSignerId() {
		return this.defaultSignerId;
	}

	public SignerConfig getDefaultSigner() {
		return getSignerById(this.defaultSignerId);
	}

	public synchronized String getSecureLabelExpression() {
		if (this.secureLabelExpression == null)
			return fixSecureLabelExpression(null);
		return this.secureLabelExpression;
	}

	public synchronized String getSecureWorkspaceSuffix() {
		if (this.secureWorkspaceSuffix == null)
			return fixSecureWorkspaceSuffix(null);
		return this.secureWorkspaceSuffix;
	}

	public SignerConfig getSignerById(final UUID uuid) {
		return this.signerMap.get(uuid);
	}

	public SignerConfig getSignerByName(final String name) {
		if (name == null)
			return null;
		return this.signerMap.values()
				.stream()
				.filter(s -> name.equals(s.getName()))
				.findFirst()
				.orElse(null);
	}

	public boolean containsSignerId(final UUID uuid) {
		return this.signerMap.containsKey(uuid);
	}

	// used by jelly & JCasC
	public List<SignerConfig> getSigners() {
		return new ArrayList<>(this.signerMap.values());
	}

	// used by jelly
	public List<SignerConfig> getSigners(final Item context) {
		// TODO actually check the owner Item!
		return new ArrayList<>(this.signerMap.values());
	}

	public void setDefaultSignerIdValue(final UUID value) {
		this.defaultSignerId = value;
	}

	@DataBoundSetter
	public void setDefaultSignerId(final String value) {
		this.defaultSignerId = IdentifierUtils.fixInvalidUUID(value);
	}

	@DataBoundSetter
	public synchronized void setSecureLabelExpression(final String value) {
		this.secureLabelExpression = fixSecureLabelExpression(value);
	}

	@DataBoundSetter
	public synchronized void setSecureWorkspaceSuffix(final String value) {
		this.secureWorkspaceSuffix = fixSecureWorkspaceSuffix(value);
	}

	@DataBoundSetter
	// used by JCasC
	public void setSigners(@NonNull final Collection<SignerConfig> col) {
		setSignersMap(col.stream()
				.collect(Collectors.toMap(SignerConfig::getUuid, Function.identity())));
	}

	public synchronized void setSignersMap(final Map<UUID, SignerConfig> signers) {
		// TODO consider using a read-write-lock
		IdentifierUtils.updateMapEntries(this.signerMap, signers);
	}

	// used by jelly
	@Override
	@Restricted(NoExternalUse.class)
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		// I wish I could telly you why the stapler doesn't work for the String values!
		setDefaultSignerId(json.optString("defaultSignerId", null));
		final JSONObject advancedObj = json.optJSONObject("advanced");
		if (advancedObj != null) {
			setSecureLabelExpression(advancedObj.optString("secureLabelExpression", null));
			setSecureWorkspaceSuffix(advancedObj.optString("secureWorkspaceSuffix", null));
		}
		// parse custom data
		setSignersMap(IdentifierUtils.bindIdentifiedEntries(req, json, SignerConfig.class, this::containsSignerId,
				"signers", IdentifiedByUUID.JSON_ID_FIELD_NAME, null));
		save();
		return true;
	}

	@Override
	public synchronized void save() {
		this.signers = getSigners();
		super.save();
	}

	@Override
	public GlobalConfigurationCategory getCategory() {
		return GlobalConfigurationCategory.get(ArtifactLifecycleCategory.class);
	}

	public ListBoxModel doFillDefaultSignerIdItems(@AncestorInPath final Item item,
			@QueryParameter final String defaultSignerId) {
		return allValuesAsListBox2(get().getSigners(item), defaultSignerId, true);
	}

	@NonNull
	public static ArtifactSignerIndex get() {
		return GlobalConfiguration.all()
				.getInstance(ArtifactSignerIndex.class);
	}

	public static String fixSecureLabelExpression(String value) {
		if ((value = Util.fixEmptyAndTrim(value)) == null)
			return DEFAULT_SECURE_LABEL_EXPRESSION;
		return value;
	}

	public static String fixSecureWorkspaceSuffix(String value) {
		if ((value = Util.fixEmptyAndTrim(value)) == null)
			return DEFAULT_SECURE_WORKSPACE_SUFFIX;
		return value;
	}

	public static ListBoxModel allValuesAsListBox(final List<SignerConfig> values) {
		final ListBoxModel model = new ListBoxModel();
		for (SignerConfig repo : values) {
			final UUID uuid = repo.getUuid();
			if (uuid == null)
				continue;
			model.add(Util.fixEmptyAndTrim(repo.getName()), uuid.toString());
		}
		return model;
	}

	public static ListBoxModel allValuesAsListBox2(final List<SignerConfig> values, String selectedId,
			final boolean withNone) {
		selectedId = IdentifierUtils.nullInvalidUUID(selectedId);
		final ListBoxModel model = new ListBoxModel();

		boolean hasValue = false;
		for (SignerConfig repo : values) {
			final UUID uuid = repo.getUuid();
			if (uuid == null)
				continue;
			final String val = uuid.toString();
			if (selectedId != null && selectedId.equals(val))
				hasValue = true;
			String name = Util.fixEmptyAndTrim(repo.getName());
			name = String.format("%s » %s", val, name == null ? "???" : name);
			model.add(name, val);
		}
		if (withNone)
			model.add(0, new Option("- none -", null));
		if (!hasValue) {
			if (selectedId == null) {
				if (!withNone)
					model.add(0, new Option("- none -", selectedId));
			} else
				model.add(0, new Option(String.format("%s » ???", selectedId), selectedId));
		}
		return model;
	}
}
