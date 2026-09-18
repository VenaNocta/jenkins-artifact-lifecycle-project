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
package org.jenkinsci.plugins.artifact.lifecycle.config;

public final class ArtifactRepositoryTags {

	private ArtifactRepositoryTags() {
	}

	public static final String TYPE = "00-repo-type";
	public static final String PROTOCOL = "10-protocol";
	public static final String CUSTOM = "custom";

	// ========================================================================
	// GENERIC
	// ========================================================================

	public static final ArtifactRepositoryTag TYPE__SOURCE = //
			new ArtifactRepositoryTag("source", TYPE, "Type.Source");
	public static final ArtifactRepositoryTag TYPE__TARGET = //
			new ArtifactRepositoryTag("target", TYPE, "Type.Target");

	public static final ArtifactRepositoryTag PROTOCOL__MAVEN2_HTTP = //
			new ArtifactRepositoryTag("maven2-http", PROTOCOL, "Protocol.Maven2.HTTP");

	// ========================================================================
	// PRODUCT SPECIFIC
	// ========================================================================

	public static final ArtifactRepositoryTag PROTOCOL__SONATYPE_NEXUS_APIv1 = //
			new ArtifactRepositoryTag("sonatype-nexus-api-v1", PROTOCOL, "Protocol.Sonatype.Nexus.APIv1");
	public static final ArtifactRepositoryTag PROTOCOL__SONATYPE_CENTRAL_PORTAL_APIv1 = //
			new ArtifactRepositoryTag("sonatype-central-portal-api-v1", PROTOCOL,
					"Protocol.Sonatype.CentralPortal.APIv1");

}
