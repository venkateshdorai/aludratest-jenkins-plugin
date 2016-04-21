/*
 * Copyright (C) 2016 Hamburg Sud and the contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aludratest.jenkins.aludratest.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;

public final class JScriptUtil {

	private JScriptUtil() {
	}

	public static String escapeStringConstantForHtml(String str) {
		// replace every character with different representations in UTF-8 and ISO-8859-1 with an underscore
		Charset iso88591 = Charset.forName("ISO-8859-1");
		Charset utf8 = Charset.forName("UTF-8");

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char ch = str.charAt(i);
			if (ch == '&') {
				sb.append("&amp;");
			}
			else if (ch == '\'') {
				sb.append("\\'");
			}
			else if (!charSameInCharsets(ch, iso88591, utf8)) {
				sb.append("_");
			}
			else {
				sb.append(ch);
			}
		}

		return sb.toString();
	}

	private static boolean charSameInCharsets(char ch, Charset charset1, Charset charset2) {
		CharsetEncoder enc1 = charset1.newEncoder();
		CharsetEncoder enc2 = charset2.newEncoder();
		if (!enc1.canEncode(ch) || !enc2.canEncode(ch)) {
			return false;
		}

		CharBuffer chbuf = CharBuffer.wrap(new char[] { ch });
		try {
			chbuf.mark();
			ByteBuffer bytes1 = enc1.encode(chbuf);
			chbuf.reset();
			ByteBuffer bytes2 = enc2.encode(chbuf);

			return Arrays.equals(bytes1.array(), bytes2.array());
		}
		catch (CharacterCodingException e) {
			return false;
		}
	}

}
