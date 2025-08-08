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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class DefaultPGPPrivateKeyCredentialsTest extends APGPTest {
	private static final Logger LOGGER = Logger.getLogger(DefaultPGPPrivateKeyCredentialsTest.class.getName());

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	protected File armoredKey;

	@Before
	public void setup() throws IOException {
		setupFile_ed25519_privateKey(this.armoredKey = tmp.newFile("ed25519-private-key.asc"));
	}

	@Test
	public void loadPrivateKey() throws Exception {
		PGPPrivateKeyCredentials credentials = load_privateKeyCredentials("ed25519-pgp-key", ED25519_PASSPHRASE,
				armoredKey);

		LOGGER.info("Fingerprint: " + toHex(credentials.getPublicKeyFingerprint()));
		assertEquals(ED25519_FINGERPRINT, toHex(credentials.getPublicKeyFingerprint()));

	}
}
