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

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Run;

public interface BomSourceProvider extends ExtensionPoint {

	/**
	 * Asks the provider to return the config
	 *
	 * @param run the Run from where the config is requested
	 * @param id  the config-id
	 * @return {@code SBomSource} when the provider has a definition covering the
	 *         given id; otherwise {@code null}
	 */
	@CheckForNull
	public BomSource getSourceById(@Nullable Run<?, ?> run, @NonNull UUID id);

	/**
	 * Asks the provider to return the config
	 *
	 * @param run  the Run from where the config is requested
	 * @param name the config-name
	 * @return {@code SBomSource} when the provider has a definition covering the
	 *         given id; otherwise {@code null}
	 */
	@CheckForNull
	public BomSource getSourceByName(@Nullable Run<?, ?> run, @NonNull String name);

	public List<BomSource> getSources(Item item);

	public static ExtensionList<BomSourceProvider> all() {
		return ExtensionList.lookup(BomSourceProvider.class);
	}

	@CheckForNull
	public static BomSource findSourceById(@Nullable final Run<?, ?> run, @NonNull final UUID id) {
		BomSource cnf = null;
		for (BomSourceProvider provider : all()) {
			cnf = provider.getSourceById(run, id);
			if (cnf != null)
				break;
		}
		return cnf;
	}

	@CheckForNull
	public static BomSource findSourceByName(@Nullable final Run<?, ?> run, @NonNull final String name) {
		BomSource cnf = null;
		for (BomSourceProvider provider : all()) {
			cnf = provider.getSourceByName(run, name);
			if (cnf != null)
				break;
		}
		return cnf;
	}

	public static List<BomSource> allSources(final Item item) {
		final List<BomSource> sources = new LinkedList<>();
		for (BomSourceProvider provider : all()) {
			final List<BomSource> lst = provider.getSources(item);
			if (lst == null)
				continue;
			lst.stream()
					.filter(Objects::nonNull)
					.forEach(sources::add);
		}
		return sources;
	}
}
