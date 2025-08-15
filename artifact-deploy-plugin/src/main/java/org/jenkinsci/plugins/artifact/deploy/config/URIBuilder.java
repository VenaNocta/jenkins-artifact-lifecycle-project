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
import java.util.Collection;

import org.apache.commons.lang.StringUtils;

public class URIBuilder implements Cloneable {

	protected String scheme = null;
	protected String userInfo = null;
	protected String host = null;
	protected int port = -1;
	protected String path = null;
	protected String query = null;
	protected String fragment = null;

	public URIBuilder(String scheme, String userInfo, String host, int port, String path, String query,
			String fragment) {
		this.scheme = StringUtils.trimToNull(scheme);
		this.userInfo = StringUtils.trimToNull(userInfo);
		this.host = StringUtils.trimToNull(host);
		this.port = port;
		this.path = StringUtils.trimToNull(path);
		this.query = StringUtils.trimToNull(query);
		this.fragment = StringUtils.trimToNull(fragment);
	}

	public URIBuilder(final URI uri) {
		this(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(),
				uri.getFragment());
	}

	// ----------------------------------------

	public String getScheme() {
		return scheme;
	}

	public String getUserInfo() {
		return userInfo;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getPath() {
		return path;
	}

	public String getQuery() {
		return query;
	}

	public String getFragment() {
		return fragment;
	}

	// ----------------------------------------

	public URIBuilder setScheme(final String scheme) {
		this.scheme = StringUtils.trimToNull(scheme);
		return this;
	}

	public URIBuilder setUserInfo(final String userInfo) {
		this.userInfo = StringUtils.trimToNull(userInfo);
		return this;
	}

	public URIBuilder setHost(final String host) {
		this.host = StringUtils.trimToNull(host);
		return this;
	}

	public URIBuilder setPort(int port) {
		this.port = port;
		return this;
	}

	public URIBuilder setPath(final String path) {
		this.path = StringUtils.trimToNull(path);
		return this;
	}

	public URIBuilder setQuery(final String query) {
		this.query = StringUtils.trimToNull(query);
		return this;
	}

	public URIBuilder setFragment(final String fragment) {
		this.fragment = StringUtils.trimToNull(fragment);
		return this;
	}

	// ----------------------------------------

	public URIBuilder withScheme(final String scheme) {
		return clone().setScheme(scheme);
	}

	public URIBuilder withUserInfo(final String userInfo) {
		return clone().setUserInfo(userInfo);
	}

	public URIBuilder withHost(final String host) {
		return clone().setHost(host);
	}

	public URIBuilder withPort(int port) {
		return clone().setPort(port);
	}

	public URIBuilder withPath(final String path) {
		return clone().setPath(path);
	}

	public URIBuilder withChildPath(final String path) {
		return clone().appendPath(path);
	}

	public URIBuilder withQuery(final String query) {
		return clone().setQuery(query);
	}

	public URIBuilder withFragment(final String fragment) {
		return clone().setFragment(fragment);
	}

	// ----------------------------------------

	public boolean hasScheme(final String scheme) {
		return StringUtils.equals(this.scheme, scheme);
	}

	public boolean hasScheme(final Collection<String> schemes) {
		return schemes.contains(this.scheme);
	}

	// ----------------------------------------

	public URIBuilder appendPath(String path) {
		path = StringUtils.trimToNull(path);
		// check for change
		if (path == null)
			return this;
		// use setter if current path is not set
		String orig = StringUtils.stripEnd(this.path, "/");
		orig = StringUtils.trimToNull(orig);
		if (orig == null)
			return setPath(path);
		// concat with '/'
		return setPath(String.join("/", orig, StringUtils.stripStart(path, "/")));
	}

	// ----------------------------------------

	public URIBuilder fixScheme(final String defaultScheme) {
		if (this.scheme == null)
			return setScheme(defaultScheme);
		return this;
	}

	public URIBuilder applyHostFix() {
		if (this.host != null || this.port != -1 || this.path == null)
			return this;
		String path = StringUtils.stripStart(this.path, "/");
		int idx = path.indexOf('/');
		if (idx == -1) {
			setHost(path);
			setPath(null);
			return this;
		}
		setHost(path.substring(0, idx));
		setPath(path.substring(idx, path.length()));
		return this;
	}

	// ----------------------------------------

	public URI toURI() throws URISyntaxException {
		return new URI(this.scheme, this.userInfo, this.host, this.port, this.path, this.query, this.fragment);
	}

	public URI toURI(final URI defaultValue) {
		try {
			return toURI();
		} catch (URISyntaxException e) {
			return defaultValue;
		}
	}

	public URL toURL() throws MalformedURLException, URISyntaxException {
		return toURI().toURL();
	}

	public URL toURL(final URL defaultValue) {
		try {
			return toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			return defaultValue;
		}
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof URIBuilder))
			return false;
		final URIBuilder other = (URIBuilder) obj;

		return StringUtils.equals(this.scheme, other.scheme) //
				&& StringUtils.equals(this.userInfo, other.userInfo) //
				&& StringUtils.equals(this.host, other.host) //
				&& this.port == other.port //
				&& StringUtils.equals(this.path, other.path) //
				&& StringUtils.equals(this.query, other.query) //
				&& StringUtils.equals(this.fragment, other.fragment);
	}

	@Override
	public URIBuilder clone() {
		return new URIBuilder(this.scheme, this.userInfo, this.host, this.port, this.path, this.query, this.fragment);
	}

	public static URIBuilder fromString(String value) throws URISyntaxException {
		if ((value = StringUtils.trimToNull(value)) == null)
			return null;
		value = StringUtils.stripStart(value, ":/");
		return new URIBuilder(new URI(value));
	}

	public static URIBuilder fromURL(final URL url) throws URISyntaxException {
		if (url == null)
			return null;
		return new URIBuilder(new URI(url.toString()));
	}
}
