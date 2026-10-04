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
package org.jenkinsci.plugins.artifact.deployment.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.artifact.lifecycle.config.AGlobalNameResolver;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifiedByUUID;
import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifierUtils;
import org.jenkinsci.plugins.artifact.lifecycle.config.NameResolver;
import org.jenkinsci.plugins.artifact.lifecycle.ui.GlobalArtifactLifecycleConfigLink.ArtifactLifecycleCategory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;

@Extension
@Symbol("globalDeploymentPipeIndex")
public class DeploymentPipeIndex extends GlobalConfiguration {

	protected transient final GlobalDeploymentPipeResolver resolver;
	protected transient final Map<UUID, PipeConfig> pipeMap = new LinkedHashMap<>();

	private Collection<PipeConfig> pipes = new LinkedList<>();

	public DeploymentPipeIndex() {
		this.resolver = new GlobalDeploymentPipeResolver(this);
		load();
	}

	@Override
	public synchronized void load() {
		super.load();
		this.pipeMap.clear();
		this.pipes.stream()
				.forEachOrdered(t -> this.pipeMap.put(t.getUuid(), t));
	}

	public PipeConfig getPipeById(final UUID uuid) {
		return this.pipeMap.get(uuid);
	}

	public boolean containsPipeId(final UUID uuid) {
		return this.pipeMap.containsKey(uuid);
	}

	// used by jelly & JCasC
	public List<PipeConfig> getPipes() {
		return new ArrayList<>(this.pipeMap.values());
	}

	// used by jelly
	public List<PipeConfig> getPipes(final Item context) {
		// TODO actually check the owner Item!
		return new ArrayList<>(this.pipeMap.values());
	}

	@DataBoundSetter
	// used by JCasC
	public void setPipes(@NonNull final Collection<PipeConfig> col) {
		setPipesMap(col.stream()
				.collect(Collectors.toMap(PipeConfig::getUuid, Function.identity())));
	}

	public synchronized void setPipesMap(final Map<UUID, PipeConfig> signers) {
		// TODO consider using a read-write-lock
		IdentifierUtils.updateMapEntries(this.pipeMap, signers);
	}

	// used by jelly
	@Override
	@Restricted(NoExternalUse.class)
	public boolean configure(final StaplerRequest2 req, final JSONObject json) throws FormException {
		// parse custom data
		setPipesMap(IdentifierUtils.bindIdentifiedEntries(req, json, PipeConfig.class, this::containsPipeId, "pipes",
				IdentifiedByUUID.JSON_ID_FIELD_NAME, null));
		save();
		return true;
	}

	@Override
	public synchronized void save() {
		this.pipes = getPipes();
		super.save();
	}

	@Override
	public GlobalConfigurationCategory getCategory() {
		return GlobalConfigurationCategory.get(ArtifactLifecycleCategory.class);
	}

	@NonNull
	public static DeploymentPipeIndex get() {
		return GlobalConfiguration.all()
				.getInstance(DeploymentPipeIndex.class);
	}

	public static GlobalDeploymentPipeResolver getResolver() {
		return get().resolver;
	}

	public static class GlobalDeploymentPipeResolver extends AGlobalNameResolver<PipeConfig, NamedPipeRef> {

		private final DeploymentPipeIndex idx;

		protected GlobalDeploymentPipeResolver(final DeploymentPipeIndex idx) {
			this.idx = idx;
		}

		@Override
		protected ExtensionList<? extends NameResolver<NamedPipeRef>> allResolvers() {
			return PipeNameResolver.all();
		}

		@Override
		public PipeConfig getEntryById(final UUID uuid) {
			return this.idx.getPipeById(uuid);
		}

		@Override
		public Stream<PipeConfig> getEntriesAsStream(final Item context) {
			// TODO actually check the owner Item!
			return this.idx.pipeMap.values()
					.parallelStream()
					.filter(Objects::nonNull);
		}
	}
}
