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
package org.jenkinsci.plugins.repoproxy.maven.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.ContextInPath;

import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import net.runeduniverse.lib.utils.common.URIBuilder;

public abstract class RepoSourceConfigDescriptor extends Descriptor<RepoSourceConfig> {

	public abstract String defaultProtocol();

	public abstract Collection<String> protocols();

	public String msgKeyRequiredError() {
		return "Source Key is required";
	}

	public String msgKeyInvalidError() {
		return "Source Key must not contains Space-Characters";
	}

	public String msgProtocolWarning() {
		return "Protocol must be any of {0} (default){1}";
	}

	public String msgUnsupportedProtocolError() {
		return "Protocol unsupported; allowed Protocols: {0} (default){1}";
	}

	public String randomKey() {
		return UUID.randomUUID()
				.toString();
	}

	@Restricted(NoExternalUse.class)
	@POST
	public FormValidation doCheckKey(@ContextInPath final ModelObject context, @QueryParameter String value) {
		if ((value = StringUtils.trimToNull(value)) == null)
			return FormValidation.error(msgKeyRequiredError());
		if (containsWhitespace(value))
			return FormValidation.error(msgKeyInvalidError());
		return FormValidation.ok();
	}

	protected boolean containsWhitespace(final String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i)))
				return true;
		}
		return false;
	}

	@Restricted(NoExternalUse.class)
	@POST
	public FormValidation doCheckUri(@AncestorInPath final Item item, @ContextInPath final ModelObject context,
			@QueryParameter String value) {
		if ((value = StringUtils.trimToNull(value)) == null)
			return FormValidation.error("URI is required");
		final URIBuilder builder;
		try {
			builder = URIBuilder.fromString(value);
		} catch (URISyntaxException e) {
			return FormValidation.error("Invalid URI");
		}

		final List<FormValidation> msgs = new LinkedList<>();
		final String scheme = builder.getScheme();
		if (scheme == null)
			msgs.add(FormValidation.warning(formatWithProtocols(msgProtocolWarning())));
		builder.fixScheme(defaultProtocol());

		if (!builder.hasScheme(protocols()))
			return FormValidation.error(formatWithProtocols(msgUnsupportedProtocolError()));

		final URI repoURI = builder.applyHostFix()
				.toURI(null);
		if (repoURI == null || repoURI.getHost() == null)
			return FormValidation.error("Invalid URL");

		testCheckUri(item, context, builder, msgs);

		return FormValidation.aggregate(msgs);
	}

	protected String formatWithProtocols(final String template) {
		final String defaultProtocol = defaultProtocol();
		final List<String> protocols = protocols().stream()
				.filter(StringUtils::isNotBlank)
				.filter(s -> !StringUtils.equals(s, defaultProtocol))
				.map(this::quoteProto)
				.toList();
		return MessageFormat.format(template, quoteProto(defaultProtocol),
				protocols.isEmpty() ? "" : " / " + String.join(" / ", protocols));
	}

	protected String quoteProto(final String s) {
		return "'" + s + "'";
	}

	protected void testCheckUri(final Item item, final ModelObject context, final URIBuilder builder,
			final List<FormValidation> msgs) {
		// NOOP
	}
}
