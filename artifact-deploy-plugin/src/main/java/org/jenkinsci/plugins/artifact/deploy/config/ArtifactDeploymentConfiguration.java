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
package org.jenkinsci.plugins.artifact.deploy.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Run;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;

@Extension
@Symbol("globalArtifactDeploymentConfig")
public class ArtifactDeploymentConfiguration extends GlobalConfiguration {

	@GuardedBy("this")
	private Map<String, ArtifactDeploymentRepository> repoMap = new LinkedHashMap<>();

	public ArtifactDeploymentConfiguration() {
		load();
	}

	public ArtifactDeploymentRepository getRepo(final String id) {
		return this.repoMap.get(id);
	}

	public synchronized List<ArtifactDeploymentRepository> getRepos() {
		return new ArrayList<ArtifactDeploymentRepository>(this.repoMap.values());
	}

	@DataBoundSetter
	public synchronized void setRepos(@NonNull List<ArtifactDeploymentRepository> repos) {
		// ignore unchanged
		this.repoMap.values()
				.retainAll(repos);
		repos.removeAll(this.repoMap.values());

		// process additions
		OUTER: for (ArtifactDeploymentRepository repo : repos) {
			if (repo == null)
				continue;
			final Descriptor<ArtifactDeploymentRepository> descriptor = repo.getDescriptor();
			// check repo id for null or duplicates
			String id = StringUtils.trimToNull(repo.getId());
			while (id == null || this.repoMap.containsKey(id)) {
				// if helper is invalid, skip repo
				if (!(descriptor instanceof ArtifactDeploymentRepositoryDescriptor))
					continue OUTER;
				final ArtifactDeploymentRepositoryDescriptor repoDesc = (ArtifactDeploymentRepositoryDescriptor) descriptor;
				id = StringUtils.trimToNull(repoDesc.randomId());
				// if id is null, skip repo
				if (id == null)
					continue OUTER;
				repo.setId(id = repoDesc.randomId());
			}
			this.repoMap.put(id, repo);
		}
		save();
	}

	@Override
	public GlobalConfigurationCategory getCategory() {
		return super.getCategory();
		// return
		// GlobalConfigurationCategory.get(ArtifactDeploymentConfiguration.Category.class);
	}

	@NonNull
	public static ArtifactDeploymentConfiguration get() {
		return GlobalConfiguration.all()
				.getInstance(ArtifactDeploymentConfiguration.class);
	}

	@Extension
	@Symbol("globalArtifactDeploymentConfig")
	public static class Category extends GlobalConfigurationCategory {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getDisplayName() {
			return "Deployment Repositories";
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getShortDescription() {
			return "Configure Deployment Repositories";
		}

	}

	// provides the global fallback values
	@Extension(ordinal = -10000)
	public static final class GlobalArtifactDeployConfigurationProvider implements ArtifactDeployConfigurationProvider {

		@Inject
		private ArtifactDeploymentConfiguration cnf;

		@Override
		public ArtifactDeploymentRepository getRepo(final Run<?, ?> run, final String id) {
			return this.cnf.getRepo(id);
		}

	}

}
