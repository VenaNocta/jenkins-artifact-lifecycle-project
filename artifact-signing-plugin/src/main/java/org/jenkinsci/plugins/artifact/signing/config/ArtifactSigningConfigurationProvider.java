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

import java.util.Objects;
import java.util.function.BiFunction;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;

public interface ArtifactSigningConfigurationProvider extends ExtensionPoint {

	@CheckForNull
	public String getDefaultCredentialsId(@Nullable Run<?, ?> run);

	public static ExtensionList<ArtifactSigningConfigurationProvider> all() {
		return ExtensionList.lookup(ArtifactSigningConfigurationProvider.class);
	}

	@CheckForNull
	public static <R> R get(@NonNull final BiFunction<ArtifactSigningConfigurationProvider, Run<?, ?>, R> function,
			@Nullable final Run<?, ?> run) {
		Objects.requireNonNull(function);
		R r = null;
		for (ArtifactSigningConfigurationProvider provider : all()) {
			r = function.apply(provider, run);
			if (r != null)
				break;
		}
		return r;
	}
}
