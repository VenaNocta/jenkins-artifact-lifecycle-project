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

import org.bouncycastle.bcpg.ArmoredInputException;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
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

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import net.jcip.annotations.GuardedBy;

public class DefaultPGPPrivateKeyCredentials extends BaseStandardCredentials implements PGPPrivateKeyCredentials {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(DefaultPGPPrivateKeyCredentials.class.getName());

	protected final Secret passphrase;
	// note: armoredKey has to be saved on disk as SecretBytes or it get's corrupted
	// but the Web requires it to be transferred as Secret for the same reason
	protected final SecretBytes armoredKey;

	/**
	 * PGP secret key
	 */
	@GuardedBy("this")
	private transient PGPSecretKey secretKey = null;
	/**
	 * Decoded PGP private key
	 */
	@GuardedBy("this")
	private transient PGPPrivateKey privateKey = null;

	public DefaultPGPPrivateKeyCredentials(@CheckForNull CredentialsScope scope, @CheckForNull String id,
			@CheckForNull String description, @CheckForNull Secret passphrase, @NonNull SecretBytes armoredKey) {
		super(scope, id, description);
		Objects.requireNonNull(armoredKey);
		this.passphrase = passphrase;
		this.armoredKey = armoredKey;
	}

	@DataBoundConstructor
	public DefaultPGPPrivateKeyCredentials(@CheckForNull CredentialsScope scope, @CheckForNull String id,
			@CheckForNull String description, @CheckForNull String passphrase, @NonNull String armoredKey) {
		this(scope, id, description, Secret.fromString(passphrase),
				SecretBytes.fromRawBytes(Secret.fromString(armoredKey)
						.getPlainText()
						.getBytes()));
	}

	@Restricted(NoExternalUse.class)
	public Secret getPassphrase() {
		return this.passphrase;
	}

	@Restricted(NoExternalUse.class)
	public Secret getArmoredKey() {
		return Secret.fromString(new String(this.armoredKey.getPlainData()));
	}

	protected synchronized PGPSecretKey getSecretKey() {
		if (this.secretKey == null) {
			try (InputStream byteSteam = new ByteArrayInputStream(this.armoredKey.getPlainData())) {
				final InputStream decoderStream = PGPUtil.getDecoderStream(byteSteam);
				final PGPSecretKeyRingCollection collection = new PGPSecretKeyRingCollection(decoderStream,
						new BcKeyFingerprintCalculator());
				LOOP: for (Iterator<PGPSecretKeyRing> i = collection.getKeyRings(); i.hasNext();) {
					final PGPSecretKeyRing keyRing = i.next();
					for (Iterator<PGPSecretKey> j = keyRing.getSecretKeys(); j.hasNext();) {
						final PGPSecretKey k = j.next();
						if (k != null) {
							this.secretKey = k;
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
		return this.secretKey;
	}

	@Override
	public synchronized PGPPrivateKey getPrivateKey() {
		final PGPSecretKey secretKey = getSecretKey();
		if (secretKey == null)
			return null;
		if (this.privateKey == null) {
			final PGPDigestCalculatorProvider calcProvider = new BcPGPDigestCalculatorProvider();
			final char[] passphraseBytes = this.passphrase.getPlainText()
					.toCharArray();
			final PBESecretKeyDecryptor decryptor = new BcPBESecretKeyDecryptorBuilder(calcProvider)
					.build(passphraseBytes);

			try {
				this.privateKey = secretKey.extractPrivateKey(decryptor);
			} catch (PGPException e) {
				LogRecord lr = new LogRecord(Level.WARNING,
						"Credentials ID {0}: Could not decrypt PGP PrivateKey, Passphrase is probably incorrect");
				lr.setParameters(new Object[] { getId() });
				lr.setThrown(e);
				LOGGER.log(lr);
			}
		}
		return this.privateKey;
	}

	@Override
	public boolean isValid() throws IOException, InterruptedException {
		return getPrivateKey() != null;
	}

	@Override
	public synchronized PGPPublicKey getPublicKey() throws IOException, InterruptedException {
		final PGPSecretKey secretKey = getSecretKey();
		if (secretKey == null)
			return null;
		return secretKey.getPublicKey();
	}

	@Override
	public synchronized boolean isMasterKey() {
		final PGPSecretKey secretKey = getSecretKey();
		if (secretKey == null)
			return false;
		return secretKey.isMasterKey();
	}

	@Override
	public synchronized boolean isSigningKey() {
		final PGPSecretKey secretKey = getSecretKey();
		if (secretKey == null)
			return false;
		return secretKey.isSigningKey();
	}

	@Override
	public synchronized byte[] getPublicKeyFingerprint() {
		final PGPSecretKey secretKey = getSecretKey();
		if (secretKey == null)
			return new byte[0];
		return secretKey.getFingerprint();
	}

	@Override
	public synchronized Iterator<String> getUserIds() {
		final PGPSecretKey secretKey = getSecretKey();
		if (secretKey == null)
			return Collections.emptyIterator();
		return secretKey.getUserIDs();
	}

	@Extension
	@Symbol("pgp-private-key")
	public static class DefaultPGPPrivateKeyCredentialsDescriptor extends BaseStandardCredentialsDescriptor {

		/**
		 * {@inheritDoc}
		 */
		@NonNull
		@Override
		public String getDisplayName() {
			return Messages.PGPPrivateKeyCredentials_Desc_DisplayName();
		}

		@Restricted(NoExternalUse.class)
		@POST
		public FormValidation doCheckArmoredKey(@QueryParameter final String value,
				@QueryParameter final String passphrase) throws IOException, ServletException {
			final String data;
			if (value == null || StringUtils.isBlank(data = Secret.fromString(value)
					.getPlainText()))
				return FormValidation.error(Messages.PGPPrivateKeyCredentials_Desc_KeyValueRequired());

			PGPSecretKey secretKey = null;

			try (InputStream byteSteam = new ByteArrayInputStream(data.getBytes())) {
				final InputStream decoderStream = PGPUtil.getDecoderStream(byteSteam);
				final PGPSecretKeyRingCollection collection = new PGPSecretKeyRingCollection(decoderStream,
						new BcKeyFingerprintCalculator());
				LOOP: for (Iterator<PGPSecretKeyRing> i = collection.getKeyRings(); i.hasNext();) {
					final PGPSecretKeyRing keyRing = i.next();
					for (Iterator<PGPSecretKey> j = keyRing.getSecretKeys(); j.hasNext();) {
						final PGPSecretKey k = j.next();
						if (k != null) {
							secretKey = k;
							break LOOP;
						}
					}
				}
			} catch (ArmoredInputException e) {
				return FormValidation.error(Messages.PGPPrivateKeyCredentials_Desc_InvalidKeyArmor());
			} catch (IOException | PGPException e) {
				return FormValidation.error(e, Messages.PGPPrivateKeyCredentials_Desc_KeyLoadingError());
			}
			if (secretKey == null)
				return FormValidation.error(Messages.PGPPrivateKeyCredentials_Desc_EmptyKeyRing());

			PGPPrivateKey privateKey = null;

			final PGPDigestCalculatorProvider calcProvider = new BcPGPDigestCalculatorProvider();
			final char[] passphraseBytes = Secret.fromString(passphrase)
					.getPlainText()
					.toCharArray();
			final PBESecretKeyDecryptor decryptor = new BcPBESecretKeyDecryptorBuilder(calcProvider)
					.build(passphraseBytes);

			try {
				privateKey = secretKey.extractPrivateKey(decryptor);
			} catch (PGPException e) {
				return FormValidation.error(e, Messages.PGPPrivateKeyCredentials_Desc_DecryptionError());
			}

			if (privateKey == null)
				return FormValidation.error(Messages.PGPPrivateKeyCredentials_Desc_DecryptionError());

			return FormValidation.ok(Messages.PGPPrivateKeyCredentials_Desc_FMT_Fingerprint(),
					Hex.encodeHexString(secretKey.getFingerprint(), false));
		}

	}
}
