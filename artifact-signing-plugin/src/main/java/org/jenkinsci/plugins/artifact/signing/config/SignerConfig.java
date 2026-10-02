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

import java.io.Serializable;

import org.jenkinsci.plugins.artifact.lifecycle.config.IdentifiedByUUID;
import org.jenkinsci.plugins.artifact.lifecycle.config.ReferenceableByName;
import org.jenkinsci.plugins.artifact.signing.engines.SigningEngine;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.model.Describable;
import hudson.model.Item;

public interface SignerConfig
		extends Describable<SignerConfig>, ReferenceableByName<NamedSignerConfigRef>, IdentifiedByUUID, Serializable {

	public boolean isValid(Item item);

	@Override
	public default NamedSignerConfigRef toNamedRef() {
		final NamedSignerConfigRef ref = new NamedSignerConfigRef();
		ref.setReadonly(true);
		ref.setUuidValue(getUuid());
		ref.setName(getName());
		return ref;
	}

	public SigningEngine createEngine(StepContext context, String signaturePrefix, String signatureSuffix);

	@Override
	public default SignerConfigDescriptor getDescriptor() {
		return (SignerConfigDescriptor) Describable.super.getDescriptor();
	}
}
