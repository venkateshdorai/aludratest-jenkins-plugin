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
package org.aludratest.jenkins.aludratest;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.aludratest.jenkins.aludratest.util.XPathUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.sf.json.JSONObject;

public class SingleTestResult {

	private String status;

	private int success;

	private boolean ignored;

	private String ignoredReason;

	public SingleTestResult(String status, int success, boolean ignored, String ignoredReason) {
		this.status = status;
		this.success = success;
		this.ignored = ignored;
		this.ignoredReason = ignoredReason;
	}

	public String getStatus() {
		return status;
	}

	public int getSuccess() {
		return success;
	}

	public boolean isIgnored() {
		return ignored;
	}

	public String getIgnoredReason() {
		return ignoredReason;
	}

	public static Map<String, SingleTestResult> fromResultsXml(Document doc) {
		Map<String, SingleTestResult> result = new LinkedHashMap<String, SingleTestResult>();

		// iterate over all test cases
		try {
			NodeList nl = XPathUtil.evalXPathAsNodeList(doc, "//testCase", XPathFactory.newInstance());

			for (int i = 0; i < nl.getLength(); i++) {
				Element elem = (Element) nl.item(i);

				String name = getElementTextContent(elem, "name");
				String status = getElementTextContent(elem, "status");
				boolean ignored = "true".equals(elem.getAttribute("ignored"));
				String ignoredReason = getElementTextContent(elem, "ignoredReason");

				result.put(name, new SingleTestResult(status, "PASSED".equals(status) ? 1 : 0, ignored, ignoredReason));
			}

			return result;
		}
		catch (XPathExpressionException e) {
			throw new RuntimeException("Invalid XPath used", e);
		}
	}

	private static String getElementTextContent(Element parent, String childElementName) {
		NodeList nl = parent.getChildNodes();

		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) n;
				if (childElementName.equals(e.getNodeName())) {
					return e.getTextContent();
				}
			}
		}

		return null;
	}

	public static SingleTestResult fromJSON(JSONObject obj) {
		// keep it simple
		return new SingleTestResult(obj.getString("status"), obj.getInt("success"), obj.getBoolean("ignored"),
				obj.has("ignoredReason") ? obj.getString("ignoredReason") : null);
	}
}