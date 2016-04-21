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

import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.NodeList;

public final class XPathUtil {

	private XPathUtil() {
	}

	public static String evalXPathAsString(Object item, String xpath, XPathFactory factory) throws XPathExpressionException {
		XPath path = factory.newXPath();
		XPathExpression expr = path.compile(xpath);
		return (String) expr.evaluate(item, XPathConstants.STRING);
	}

	public static int evalXPathAsInt(Object item, String xpath, XPathFactory factory) throws XPathExpressionException {
		XPath path = factory.newXPath();
		XPathExpression expr = path.compile(xpath);
		Number result = (Number) expr.evaluate(item, XPathConstants.NUMBER);
		return result == null ? 0 : result.intValue();
	}

	public static NodeList evalXPathAsNodeList(Object item, String xpath, XPathFactory factory) throws XPathExpressionException {
		XPath path = factory.newXPath();
		XPathExpression expr = path.compile(xpath);
		return (NodeList) expr.evaluate(item, XPathConstants.NODESET);
	}

	public static List<String> evalXPathAsStringList(Object item, String xpath, XPathFactory factory, boolean includeDuplicates)
			throws XPathExpressionException {
		NodeList nl = evalXPathAsNodeList(item, xpath, factory);

		List<String> result = new ArrayList<String>(nl.getLength());

		for (int i = 0; i < nl.getLength(); i++) {
			String text = nl.item(i).getTextContent();
			if (includeDuplicates || !result.contains(text)) {
				result.add(text);
			}
		}

		return result;
	}

}
