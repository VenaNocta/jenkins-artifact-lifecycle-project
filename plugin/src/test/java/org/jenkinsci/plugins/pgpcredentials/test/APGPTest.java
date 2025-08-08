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
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pgpcredentials.DefaultPGPPrivateKeyCredentials;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;

import hudson.util.Secret;

public class APGPTest {

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	protected static final String ED25519_PASSPHRASE = "TestTeam123";
	protected static final String ED25519_FINGERPRINT = "04D7FBCB3E1F93D8E99F8EC185788792058B9450";

	protected static CredentialsScope DEFAULT_SCOPE = CredentialsScope.SYSTEM;
	protected static String DEFAULT_DESCRIPTION = "???";

	protected void setupFile_ed25519_privateKey(final File armoredKey) throws IOException {
		FileUtils.copyURLToFile(APGPTest.class.getResource("ed25519.key"), armoredKey);
	}

	protected PGPPrivateKeyCredentials load_privateKeyCredentials(String id, String passphrase, File armoredKey)
			throws IOException {
		return load_privateKeyCredentials(DEFAULT_SCOPE, id, DEFAULT_DESCRIPTION, passphrase, armoredKey);
	}

	protected PGPPrivateKeyCredentials load_privateKeyCredentials(CredentialsScope scope, String id, String passphrase,
			File armoredKey) throws IOException {
		return load_privateKeyCredentials(scope, id, DEFAULT_DESCRIPTION, passphrase, armoredKey);
	}

	protected PGPPrivateKeyCredentials load_privateKeyCredentials(CredentialsScope scope, String id, String desc,
			String passphrase, File armoredKey) throws IOException {
		SecretBytes armoredKeyBytes = getBytesFromFile(armoredKey);
		return new DefaultPGPPrivateKeyCredentials(scope, id, desc, Secret.fromString(passphrase), armoredKeyBytes);
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
