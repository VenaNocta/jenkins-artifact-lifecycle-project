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
package org.jenkinsci.plugins.repoproxy.maven;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;

public class ArtifactRequestTracker implements ArtifactValidator {

	protected final Consumer<Serializable> callback;
	protected final boolean passAll;

	public ArtifactRequestTracker(final Consumer<Serializable> callback, final boolean passAll) {
		this.callback = callback;
		this.passAll = passAll;
	}

	@Override
	public boolean validate(final ArtifactProvider provider, final ArtifactData data) throws InvalidArtifactException {
		if (data != null)
			this.callback.accept(new Data(data.getPURL(), data.getChecksums()));
		return this.passAll;
	}

	public static class Data implements Serializable {

		private static final long serialVersionUID = 1L;

		protected final String purl;
		// Map<Algorithm, Value>
		protected final Map<String, String> checksums;

		public Data(final String purl, final Map<String, String> checksums) {
			this.purl = purl;
			this.checksums = new HashMap<String, String>(checksums);
		}

		public String getPurl() {
			return this.purl;
		}

		public Map<String, String> getChecksums() {
			return this.checksums;
		}

		@Override
		public int hashCode() {
			return this.purl.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (obj instanceof Data other) {
				return this.purl.equals(other.getPurl()) && this.checksums.equals(other.getChecksums());
			}
			return false;
		}
	}
}
