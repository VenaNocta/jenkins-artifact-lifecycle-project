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

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;

public class PGPSignatureGeneratorBuilder {

	// format: Your Name <your@email.com>
	protected final Set<String> userIdSet = new LinkedHashSet<>();

	protected PGPPrivateKey privateKey;
	protected PGPPublicKey publicKey;

	protected int hashAlgo = HashAlgorithmTags.SHA256;

	protected int signatureType = PGPSignature.BINARY_DOCUMENT;

	public void setKeys(final PGPPrivateKey privateKey, final PGPPublicKey publicKey) {
		this.privateKey = privateKey;
		this.publicKey = publicKey;
	}

	public void setHashAlgorithm(final int hashAlgo) {
		this.hashAlgo = hashAlgo;
	}

	public void setSignatureType(final int signatureType) {
		this.signatureType = signatureType;
	}

	public void setSignatureType_BinaryDocument() {
		this.signatureType = PGPSignature.BINARY_DOCUMENT;
	}

	public void setSignatureType_CanonicalTextDocument() {
		this.signatureType = PGPSignature.CANONICAL_TEXT_DOCUMENT;
	}

	public void setSignatureType_StandAlone() {
		this.signatureType = PGPSignature.STAND_ALONE;
	}

	public void addSignerUserID(final String userId) {
		this.userIdSet.add(userId);
	}

	public void addSignerUser(final String name, final String email) {
		this.userIdSet.add(String.format("%s <%s>", name, email));
	}

	public void withCredentials(final PGPPrivateKeyCredentials credentials) throws IOException, InterruptedException {
		this.privateKey = credentials.getPrivateKey();
		this.publicKey = credentials.getPublicKey();
		for (Iterator<String> i = credentials.getUserIds(); i.hasNext();) {
			this.userIdSet.add(i.next());
		}
	}

	public PGPSignatureGenerator build() throws PGPException {
		final PGPContentSignerBuilder signerBuilder = new BcPGPContentSignerBuilder(this.publicKey.getAlgorithm(),
				this.hashAlgo);

		final PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(signerBuilder, this.publicKey);
		signatureGenerator.init(this.signatureType, this.privateKey);

		if (PGPSignature.BINARY_DOCUMENT != this.signatureType) {
			final PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();

			for (String userId : userIdSet) {
				spGen.addSignerUserID(false, userId);
			}

			signatureGenerator.setHashedSubpackets(spGen.generate());
		}

		return signatureGenerator;
	}

}
