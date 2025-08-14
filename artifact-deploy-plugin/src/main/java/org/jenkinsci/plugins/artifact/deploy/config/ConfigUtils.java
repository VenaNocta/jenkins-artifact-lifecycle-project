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
package org.jenkinsci.plugins.artifact.deploy.config;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class ConfigUtils {

	public static URL convertURL(String url, String defaultProtocol, List<String> protocols) {
		if ((url = StringUtils.trimToNull(url)) == null)
			return null;
		url = StringUtils.stripStart(url, "://");
		// fix protocols if null
		if (protocols == null)
			protocols = List.of();
		// try to get the default protocol
		if (defaultProtocol == null) {
			final Iterator<String> i = protocols.iterator();
			if (i.hasNext())
				defaultProtocol = i.next();
		}

		URI uri;
		try {
			uri = new URI(url);
			final String scheme = uri.getScheme();

			if (scheme == null) {
				if (defaultProtocol == null)
					return null;
				uri = new URI(defaultProtocol, uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(),
						uri.getQuery(), uri.getFragment());
			} else {
				if (!protocols.isEmpty() && !protocols.contains(scheme))
					return null;
			}
		} catch (URISyntaxException e) {
			if (defaultProtocol == null)
				return null;
			try {
				uri = new URI(defaultProtocol + "://" + url);
			} catch (URISyntaxException e1) {
				return null;
			}
		}
		try {
			return uri.toURL();
		} catch (MalformedURLException e) {
			return null;
		}
	}

	public static URL appendPath(URL url, String path) throws URISyntaxException, MalformedURLException {
		path = StringUtils.trimToNull(path);
		if (url == null || path == null)
			return url;

		URI uri = url.toURI();

		final String origPath = StringUtils.stripEnd(uri.getPath(), "/");
		path = StringUtils.stripStart(path, "/");

		uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
				String.join("/", origPath, path), uri.getQuery(), uri.getFragment());

		return uri.toURL();
	}

}
