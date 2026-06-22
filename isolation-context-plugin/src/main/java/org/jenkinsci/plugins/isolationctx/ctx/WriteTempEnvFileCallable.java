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
package org.jenkinsci.plugins.isolationctx.ctx;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.io.Files;

import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

public class WriteTempEnvFileCallable extends MasterToSlaveFileCallable<String> {

	private static final long serialVersionUID = 1L;

	protected final Map<String, String> env;
	protected final boolean exportVariables;

	public WriteTempEnvFileCallable(final Map<String, String> env, final boolean exportVariables) {
		this.env = env;
		this.exportVariables = exportVariables;
	}

	@Override
	public String invoke(final File wsDir, final VirtualChannel channel) throws IOException, InterruptedException {
		final File envFile = File.createTempFile("cmd-", ".env", wsDir);

		final StringBuilder builder = new StringBuilder();
		final String prefix = this.exportVariables ? "export " : "";
		for (Entry<String, String> entry : this.env.entrySet())
			builder.append(prefix + entry.getKey() + "=" + singleQuote(entry.getValue()) + "\n");

		Files.write(builder.toString()
				.getBytes(), envFile);

		return envFile.getAbsolutePath();
	}

	public static String singleQuote(final String value) {
		if (value == null) {
			return "''";
		}
		// replace ' with '\'' (close quote, escaped quote, reopen)
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}
}