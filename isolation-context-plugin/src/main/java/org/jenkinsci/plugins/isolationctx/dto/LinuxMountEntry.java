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
package org.jenkinsci.plugins.isolationctx.dto;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class LinuxMountEntry implements Serializable {

	private static final long serialVersionUID = 1L;

	protected final List<String> options = new LinkedList<>();

	protected final String type;
	protected final String source;
	protected final String destination;

	public LinuxMountEntry(final String type, final String source, final String destination) {
		this.type = type;
		this.source = source;
		this.destination = destination;
	}

	public LinuxMountEntry options(final String... options) {
		if (options == null)
			return this;
		for (int i = 0; i < options.length; i++)
			this.options.add(options[i]);
		return this;
	}

	public String toOciJson() {
		final String json = "{\"destination\":\"" + this.destination //
				+ "\",\"type\":\"" + this.type //
				+ "\",\"source\":\"" + this.source + "\"";
		if (this.options.isEmpty())
			return json + "}";
		return json + ",\"options\":[\"" + String.join("\",\"", this.options) + "\"]}";
	}
}