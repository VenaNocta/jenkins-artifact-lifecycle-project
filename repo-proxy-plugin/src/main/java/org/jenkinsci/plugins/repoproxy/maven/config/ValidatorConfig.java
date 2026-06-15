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
import java.util.Set;
import java.util.UUID;

import hudson.ExtensionPoint;
import hudson.model.Describable;

public abstract class ValidatorConfig implements Describable<ValidatorConfig>, Serializable, ExtensionPoint {

	private static final long serialVersionUID = 1L;

	protected ValidatorConfig() {
	}

	public abstract boolean isValid();

	public abstract Set<UUID> getRequiredSBoms();

	@Override
	public ValidatorConfigDescriptor getDescriptor() {
		return (ValidatorConfigDescriptor) Describable.super.getDescriptor();
	}

	public static boolean checkValid(final ValidatorConfig config) {
		return config != null && config.isValid();
	}
}
