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
package org.jenkinsci.plugins.repoproxy.steps;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

public class TrackedArtifactsAction implements PersistentAction {

	// source - PURL
	protected final Map<String, Set<String>> artifactsBySource = new LinkedHashMap<>();

	@Override
	public String getIconFileName() {
		return "folder.png";
	}

	@Override
	public String getDisplayName() {
		return "Tracked Artifacts";
	}

	@Override
	public String getUrlName() {
		return "trackedArtifacts";
	}

	public synchronized void addArtifacts(final String source, Set<String> data) {
		Objects.requireNonNull(data);
		data = new HashSet<>(data);
		data.remove(null);
		this.artifactsBySource.computeIfAbsent(source, k -> new HashSet<>())
				.addAll(data);
	}

	public int getTotalCnt() {
		final Set<String> cnt = new LinkedHashSet<>();
		for (Set<String> set : this.artifactsBySource.values())
			cnt.addAll(set);
		return cnt.size();
	}

	protected StringBuilder sourceReport(final String source, final Set<String> data) {
		return new StringBuilder("Source: ") //
				.append(source)
				.append("\n  total: ")
				.append(data.size())
				.append('\n');
	}

	protected StringBuilder sourceListing(final String source, final Set<String> data) {
		final StringBuilder text = new StringBuilder("Repository: ").append(source);
		for (String purl : data) {
			text.append("\n  ")
					.append(purl);
		}
		return text.append("\n\n");
	}

	public HttpResponse doDynamic() throws IOException {
		final StringBuilder summuary = new StringBuilder("Artifact Tracker\n");
		final StringBuilder listing = new StringBuilder("\n\n--- Artifacts per Repository ---\n");
		summuary.append("total: " + getTotalCnt());
		for (Entry<String, Set<String>> entry : this.artifactsBySource.entrySet()) {
			final String repoKey = entry.getKey();
			final Set<String> data = entry.getValue();
			summuary.append("\n-----------\n");
			summuary.append(sourceReport(repoKey, data));
			listing.append(sourceListing(repoKey, data));
		}
		return HttpResponses.text(summuary.append(listing)
				.toString());
	}

	public void merge(final TrackedArtifactsAction other) {
		if (other == null)
			return;
		other.artifactsBySource.forEach(this::addArtifacts);
	}

	public static TrackedArtifactsAction merge(final TrackedArtifactsAction action,
			final TrackedArtifactsAction other) {
		if (action != null) {
			action.merge(other);
			return action;
		}
		return other;
	}
}
