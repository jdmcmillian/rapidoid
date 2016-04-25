/*
 * Copyright (c) 2015 Chris Mason <chris@netnix.org>
 * Copyright (c) 2016 Nikolche Mihajlovski (MODIFIED the original version)
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package org.netnix;

import keywhiz.hkdf.Hkdf;
import org.rapidoid.annotation.Authors;
import org.rapidoid.annotation.Since;
import org.rapidoid.crypto.Crypto;
import org.rapidoid.u.U;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;

@Authors({"Chris Mason", "Nikolche Mihajlovski"})
@Since("5.1.0")
public class AES {

	private static final Hkdf HKDF = Hkdf.usingDefaults();

	private static final String HMAC_SHA_256 = "HmacSHA256";
	private static final String AES_CTR_NO_PADDING = "AES/CTR/NoPadding";

	public static byte[] generateKey(String password, byte[] salt, int iterations, int length) throws Exception {
		SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		PBEKeySpec ks = new PBEKeySpec(password.toCharArray(), salt, iterations, length);
		return skf.generateSecret(ks).getEncoded();
	}

	public static byte[] encrypt(byte[] data, byte[] secret) throws Exception {
		byte[] aesSalt = randomSalt();
		byte[] aesKey = hkdf(secret, aesSalt, 256);
		SecretKeySpec aesKeySpec = new SecretKeySpec(aesKey, "AES");

		Cipher cipher = Cipher.getInstance(AES_CTR_NO_PADDING);
		cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new IvParameterSpec(new byte[16]));
		byte[] encrypted = cipher.doFinal(data);

		byte[] hmacSalt = randomSalt();
		byte[] hmacKey = hkdf(secret, hmacSalt, 160);
		SecretKeySpec hmacKeySpec = new SecretKeySpec(hmacKey, HMAC_SHA_256);

		Mac m = Mac.getInstance(HMAC_SHA_256);
		m.init(hmacKeySpec);
		byte[] hmac = m.doFinal(encrypted);

		byte[] os = new byte[40 + encrypted.length + 32];
		System.arraycopy(aesSalt, 0, os, 0, 20);
		System.arraycopy(hmacSalt, 0, os, 20, 20);
		System.arraycopy(encrypted, 0, os, 40, encrypted.length);
		System.arraycopy(hmac, 0, os, 40 + encrypted.length, 32);
		return os;
	}

	public static byte[] decrypt(byte[] data, byte[] secret) throws Exception {

		U.must(data.length >= 72, "Not enough data to decrypt!");

		byte[] aesSalt = Arrays.copyOfRange(data, 0, 20);
		byte[] hmacSalt = Arrays.copyOfRange(data, 20, 40);
		byte[] encrypted = Arrays.copyOfRange(data, 40, data.length - 32);
		byte[] hmac = Arrays.copyOfRange(data, data.length - 32, data.length);

		byte[] hmacKey = hkdf(secret, hmacSalt, 160);
		SecretKeySpec hmacKeySpec = new SecretKeySpec(hmacKey, HMAC_SHA_256);

		Mac m = Mac.getInstance(HMAC_SHA_256);
		m.init(hmacKeySpec);
		byte[] expectedHmac = m.doFinal(encrypted);

		if (MessageDigest.isEqual(hmac, expectedHmac)) {
			byte[] decrKey = hkdf(secret, aesSalt, 256);
			SecretKeySpec dekrKeySpec = new SecretKeySpec(decrKey, "AES");

			Cipher cipher = Cipher.getInstance(AES_CTR_NO_PADDING);
			cipher.init(Cipher.DECRYPT_MODE, dekrKeySpec, new IvParameterSpec(new byte[16]));
			return cipher.doFinal(encrypted);

		} else {
			throw U.rte("Cannot decrypt corrupted data!");
		}
	}

	private static byte[] hkdf(byte[] secret, byte[] salt, int bitLength) {
		SecretKeySpec key = new SecretKeySpec(secret, HMAC_SHA_256);
		return HKDF.expand(key, salt, bitLength / 8);
	}

	private static byte[] randomSalt() {
		byte[] salt = new byte[20];
		Crypto.RANDOM.nextBytes(salt);
		return salt;
	}

}