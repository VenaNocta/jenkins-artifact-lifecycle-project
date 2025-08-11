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
package org.jenkinsci.plugins.pgpcredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.bcpg.ArmoredInputException;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPublicKeyCredentials;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import net.jcip.annotations.GuardedBy;

public class DefaultPGPPublicKeyCredentials extends BaseStandardCredentials implements PGPPublicKeyCredentials {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(DefaultPGPPublicKeyCredentials.class.getName());

	// note: armoredKey has to be saved on disk as SecretBytes or it get's corrupted
	// but the Web requires it to be transferred as Secret for the same reason
	protected final SecretBytes armoredKey;

	/**
	 * PGP secret key
	 */
	@GuardedBy("this")
	private transient PGPPublicKey publicKey = null;

	public DefaultPGPPublicKeyCredentials(@CheckForNull CredentialsScope scope, @CheckForNull String id,
			@CheckForNull String description, @NonNull SecretBytes armoredKey) {
		super(scope, id, description);
		Objects.requireNonNull(armoredKey);
		this.armoredKey = armoredKey;
	}

	@DataBoundConstructor
	public DefaultPGPPublicKeyCredentials(@CheckForNull CredentialsScope scope, @CheckForNull String id,
			@CheckForNull String description, @NonNull String armoredKey) {
		this(scope, id, description, SecretBytes.fromRawBytes(Secret.fromString(armoredKey)
				.getPlainText()
				.getBytes()));
	}

	public Secret getArmoredKey() {
		return Secret.fromString(new String(this.armoredKey.getPlainData()));
	}

	@Override
	public synchronized PGPPublicKey getPublicKey() {
		if (this.publicKey == null) {
			try (InputStream byteSteam = new ByteArrayInputStream(this.armoredKey.getPlainData())) {
				final InputStream decoderStream = PGPUtil.getDecoderStream(byteSteam);
				final PGPPublicKeyRingCollection collection = new PGPPublicKeyRingCollection(decoderStream,
						new BcKeyFingerprintCalculator());
				LOOP: for (Iterator<PGPPublicKeyRing> i = collection.getKeyRings(); i.hasNext();) {
					final PGPPublicKeyRing keyRing = i.next();
					for (Iterator<PGPPublicKey> j = keyRing.getPublicKeys(); j.hasNext();) {
						final PGPPublicKey k = j.next();
						if (k != null) {
							this.publicKey = k;
							break LOOP;
						}
					}
				}
			} catch (IOException | PGPException e) {
				LogRecord lr = new LogRecord(Level.SEVERE,
						"Credentials ID {0}: Could not load PGP KeyRing from KeyStore");
				lr.setParameters(new Object[] { getId() });
				lr.setThrown(e);
				LOGGER.log(lr);
			}
		}
		return this.publicKey;
	}

	@Override
	public boolean isValid() throws IOException, InterruptedException {
		return getPublicKey() != null;
	}

	@Override
	public synchronized boolean isMasterKey() {
		final PGPPublicKey publicKey = getPublicKey();
		if (publicKey == null)
			return false;
		return publicKey.isMasterKey();
	}

	@Override
	public synchronized byte[] getPublicKeyFingerprint() {
		final PGPPublicKey publicKey = getPublicKey();
		if (publicKey == null)
			return new byte[0];
		return publicKey.getFingerprint();
	}

	@Override
	public synchronized Iterator<String> getUserIds() {
		final PGPPublicKey publicKey = getPublicKey();
		if (publicKey == null)
			return Collections.emptyIterator();
		return publicKey.getUserIDs();
	}

	@Extension
	@Symbol("pgp-public-key")
	public static class DefaultPGPPublicKeyCredentialsDescriptor extends BaseStandardCredentialsDescriptor {

		/**
		 * {@inheritDoc}
		 */
		@NonNull
		@Override
		public String getDisplayName() {
			return Messages.DefaultPGPPublicKeyCredentialsDescriptor_DisplayName();
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckArmoredKey(@QueryParameter final String value)
				throws IOException, ServletException {
			final String data;
			if (value == null || StringUtils.isBlank(data = Secret.fromString(value)
					.getPlainText()))
				return FormValidation.error(Messages.DefaultPGPPublicKeyCredentialsDescriptor_KeyValueRequired());

			PGPPublicKey publicKey = null;

			try (InputStream byteSteam = new ByteArrayInputStream(data.getBytes())) {
				final InputStream decoderStream = PGPUtil.getDecoderStream(byteSteam);
				final PGPPublicKeyRingCollection collection = new PGPPublicKeyRingCollection(decoderStream,
						new BcKeyFingerprintCalculator());
				LOOP: for (Iterator<PGPPublicKeyRing> i = collection.getKeyRings(); i.hasNext();) {
					final PGPPublicKeyRing keyRing = i.next();
					for (Iterator<PGPPublicKey> j = keyRing.getPublicKeys(); j.hasNext();) {
						final PGPPublicKey k = j.next();
						if (k != null) {
							publicKey = k;
							break LOOP;
						}
					}
				}
			} catch (ArmoredInputException e) {
				return FormValidation.error(Messages.DefaultPGPPublicKeyCredentialsDescriptor_InvalidKeyArmor());
			} catch (IOException | PGPException e) {
				return FormValidation.error(e, Messages.DefaultPGPPublicKeyCredentialsDescriptor_KeyLoadingError());
			}
			if (publicKey == null)
				return FormValidation.error(Messages.DefaultPGPPublicKeyCredentialsDescriptor_EmptyKeyRing());

			return FormValidation.ok(Messages.DefaultPGPPublicKeyCredentialsDescriptor_TMPL_Fingerprint(),
					Hex.encodeHexString(publicKey.getFingerprint(), false));
		}

	}
}
