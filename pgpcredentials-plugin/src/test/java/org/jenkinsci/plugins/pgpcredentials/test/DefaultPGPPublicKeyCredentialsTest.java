/*
 * Copyright © 2025 VenaNocta (venanocta@gmail.com)
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
package org.jenkinsci.plugins.pgpcredentials.test;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.jenkinsci.plugins.pgpcredentials.api.PGPPublicKeyCredentials;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class DefaultPGPPublicKeyCredentialsTest extends APGPTest {
	private static final Logger LOGGER = Logger.getLogger(DefaultPGPPublicKeyCredentialsTest.class.getName());

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	protected File armoredKey;

	@Before
	public void setup() throws IOException {
		setupFile_ed25519_publicKey(this.armoredKey = tmp.newFile("ed25519-public-key.asc"));
	}

	@Test
	public void loadPublicKey() throws Exception {
		PGPPublicKeyCredentials credentials = load_publicKeyCredentials("ed25519-pgp-pub", this.armoredKey);

		LOGGER.info("Fingerprint: " + toHex(credentials.getPublicKeyFingerprint()));
		assertEquals(ED25519_FINGERPRINT, toHex(credentials.getPublicKeyFingerprint()));
	}
}
