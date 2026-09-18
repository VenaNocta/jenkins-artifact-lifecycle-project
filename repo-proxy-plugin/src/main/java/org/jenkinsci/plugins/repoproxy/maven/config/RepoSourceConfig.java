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

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.GlobalRepositoryIndex;
import org.jenkinsci.plugins.artifact.lifecycle.maven.config.Maven2Repository;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.common.IdCredentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Describable;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;

public abstract class RepoSourceConfig implements Describable<RepoSourceConfig>, Serializable, ExtensionPoint {

	private static final long serialVersionUID = 1L;

	protected String key;
	@CheckForNull
	protected UUID repoId;

	protected RepoSourceConfig(@NonNull final String key) {
		setKey(key);
	}

	public String getKey() {
		return this.key;
	}

	@CheckForNull
	public UUID getRepoId() {
		return this.repoId;
	}

	public Maven2Repository getRepo() {
		final ArtifactRepository repository = GlobalRepositoryIndex.get()
				.getRepoById(this.repoId);
		if (Maven2Repository.isSupported(repository))
			return (Maven2Repository) repository;
		return null;
	}

	public Set<UUID> getRepoIds() {
		return Set.of(this.repoId);
	}

	public Set<String> getCredentialIds() {
		return null;
	}

	public void setKey(@NonNull String key) {
		if ((key = Util.fixEmptyAndTrim(key)) == null)
			key = getDescriptor().randomKey();
		this.key = key;
	}

	public void setRepoIdValue(final UUID value) {
		this.repoId = value;
	}

	@DataBoundSetter
	public void setRepoId(String value) {
		try {
			value = Util.fixEmptyAndTrim(value);
			this.repoId = value == null ? null : UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			this.repoId = null;
		}
	}

	public abstract RepositorySource buildSource(Path repoPath, Map<UUID, Maven2Repository> repoMap,
			Map<String, IdCredentials> credentialMap);

	@Override
	public RepoSourceConfigDescriptor getDescriptor() {
		return (RepoSourceConfigDescriptor) Describable.super.getDescriptor();
	}
}
