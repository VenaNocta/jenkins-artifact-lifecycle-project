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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;

public interface SignerNameResolver extends ExtensionPoint {

	// when result is null -> try next resolver
	// when Optional is empty -> the name is not available / inheritance is
	// disabled!
	public Optional<UUID> resolveName(@NonNull Item item, @Nullable String name);

	// when false -> inheritance is disabled!
	public boolean collectNameMappings(@NonNull Item item, @NonNull Set<NamedSignerConfigRef> nameMappings);

	public static ExtensionList<SignerNameResolver> all() {
		return ExtensionList.lookup(SignerNameResolver.class);
	}
}
