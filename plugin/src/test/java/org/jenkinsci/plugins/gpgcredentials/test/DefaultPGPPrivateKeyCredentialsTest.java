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
package org.jenkinsci.plugins.gpgcredentials.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pgpcredentials.DefaultPGPPrivateKeyCredentials;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;

import hudson.util.Secret;

public class DefaultPGPPrivateKeyCredentialsTest {
	private static final Logger LOGGER = Logger.getLogger(DefaultPGPPrivateKeyCredentialsTest.class.getName());

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	protected static final String VALID_PASSPHRASE = "TestTeam123";
	protected static final String ED25519_FINGERPRINT = "04D7FBCB3E1F93D8E99F8EC185788792058B9450";

	protected File armoredKey;

	@Before
	public void setup() throws IOException {
		this.armoredKey = tmp.newFile("ed25519-private-key.asc");
		FileUtils.copyURLToFile(DefaultPGPPrivateKeyCredentialsTest.class.getResource("private-key.asc"),
				this.armoredKey);
	}

	@Test
	public void loadPrivateKey() throws Exception {
		SecretBytes armoredKeyBytes = getBytesFromFile(this.armoredKey);
		PGPPrivateKeyCredentials credentials = new DefaultPGPPrivateKeyCredentials(CredentialsScope.SYSTEM,
				"ed25519-pgp-key", "???", Secret.fromString(VALID_PASSPHRASE), armoredKeyBytes);

		LOGGER.info("Fingerprint: " + toHex(credentials.getPublicKeyFingerprint()));
		assertEquals(ED25519_FINGERPRINT, toHex(credentials.getPublicKeyFingerprint()));

	}

	protected SecretBytes getBytesFromFile(File file) throws IOException {
		return SecretBytes.fromBytes(Files.readAllBytes(file.toPath()));
	}

	public String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			builder.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
			builder.append(Character.forDigit((bytes[i] & 0xF), 16));
		}
		return builder.toString()
				.toUpperCase();
	}
}
