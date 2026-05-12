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
package org.jenkinsci.plugins.ctx.proxy.repo.maven.config;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.utils.common.StringUtils;
import net.runeduniverse.lib.utils.common.URIBuilder;

public abstract class RepoSourceConfig implements Describable<RepoSourceConfig>, Serializable, ExtensionPoint {

	private static final long serialVersionUID = 1L;

	protected String key;
	@CheckForNull
	protected URI uri;

	protected RepoSourceConfig(@NonNull final String key) {
		setKey(key);
	}

	public String getKey() {
		return this.key;
	}

	public URI getUri() {
		return this.uri;
	}

	public void setKey(@NonNull String key) {
		if ((key = StringUtils.trimToNull(key)) == null)
			key = getDescriptor().randomKey();
		this.key = key;
	}

	protected void setUri(final URIBuilder builder) {
		if (builder == null) {
			this.uri = null;
			return;
		}
		final RepoSourceConfigDescriptor descriptor = getDescriptor();
		builder.fixScheme(descriptor.defaultProtocol())
				.applyHostFix();
		this.uri = builder.hasScheme(descriptor.protocols()) ? builder.toURI(null) : null;
	}

	public void setUri(final URI uri) {
		if (uri == null)
			this.uri = null;
		else
			setUri(new URIBuilder(uri));
	}

	@DataBoundSetter
	public void setUri(final String uri) {
		URIBuilder builder = null;
		try {
			builder = URIBuilder.fromString(uri);
		} catch (URISyntaxException ignored) {
		}
		setUri(builder);
	}

	public abstract RepositorySource buildSource(Path repoPath);

	@Override
	public RepoSourceConfigDescriptor getDescriptor() {
		return (RepoSourceConfigDescriptor) Describable.super.getDescriptor();
	}
}
