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
package org.jenkinsci.plugins.isolationctx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import hudson.FilePath;

public enum Scripts {

	// jar:/scripts/data/linux-mount-python3.py
	DATA__LINUX_MOUNT_PYTHON3("data/linux-mount-python3.py"),
	// jar:/scripts/firewall/nftables-python3.py
	FIREWALL__NFTABLES_PYTHON3("firewall/nftables-python3.py"),
	// jar:/scripts/network/elinux-python3.py
	NETWORK__ENTERPRISE_LINUX_PYTHON3("network/elinux-python3.py"),
	// jar:/scripts/runtime/linux-crun.sh
	RUNTIME__LINUX_CRUN_SHELL("runtime/linux-crun.sh");

	protected final String embeddedScript;

	Scripts(final String embeddedScript) {
		this.embeddedScript = embeddedScript;
	}

	public void deployTo(final FilePath targetFilePath) throws IOException, InterruptedException {
		// deploy file from resources -> path
		try (final InputStream input = getClass().getResourceAsStream("/scripts/" + this.embeddedScript)) {
			try (OutputStream out = targetFilePath.write()) {
				org.apache.commons.io.IOUtils.copy(input, out);
			}
		}
	}
}
