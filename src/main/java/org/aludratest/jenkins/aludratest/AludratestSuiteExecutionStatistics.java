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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.aludratest.jenkins.aludratest.util.XPathUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

public class AludratestSuiteExecutionStatistics {

	private String suiteName;

	private int numberOfTests;

	private int numberOfFailedTests;

	private int numberOfSuccessfulTests;

	private int numberOfIgnoredTests;

	private int numberOfIgnoredFailedTests;

	private int numberOfIgnoredSuccessfulTests;

	private Map<String, Integer> numberOfTestsByStatus = new HashMap<String, Integer>();

	private List<AludratestSuiteExecutionStatistics> childSuites = new ArrayList<AludratestSuiteExecutionStatistics>();

	public void setSuiteName(String suiteName) {
		this.suiteName = suiteName;
	}

	public String getSuiteName() {
		return suiteName;
	}

	public List<AludratestSuiteExecutionStatistics> getChildSuites() {
		return childSuites;
	}

	public void setChildSuites(List<AludratestSuiteExecutionStatistics> childSuites) {
		this.childSuites = childSuites;
	}

	public int getNumberOfTests() {
		return numberOfTests;
	}

	public void setNumberOfTests(int numberOfTests) {
		this.numberOfTests = numberOfTests;
	}

	public int getNumberOfFailedTests() {
		return numberOfFailedTests;
	}

	public void setNumberOfFailedTests(int numberOfFailedTests) {
		this.numberOfFailedTests = numberOfFailedTests;
	}

	public int getNumberOfSuccessfulTests() {
		return numberOfSuccessfulTests;
	}

	public void setNumberOfSuccessfulTests(int numberOfSuccessfulTests) {
		this.numberOfSuccessfulTests = numberOfSuccessfulTests;
	}

	public int getNumberOfIgnoredTests() {
		return numberOfIgnoredTests;
	}

	public void setNumberOfIgnoredTests(int numberOfIgnoredTests) {
		this.numberOfIgnoredTests = numberOfIgnoredTests;
	}

	public int getNumberOfIgnoredFailedTests() {
		return numberOfIgnoredFailedTests;
	}

	public void setNumberOfIgnoredFailedTests(int numberOfIgnoredFailedTests) {
		this.numberOfIgnoredFailedTests = numberOfIgnoredFailedTests;
	}

	public int getNumberOfIgnoredSuccessfulTests() {
		return numberOfIgnoredSuccessfulTests;
	}

	public void setNumberOfIgnoredSuccessfulTests(int numberOfIgnoredSuccessfulTests) {
		this.numberOfIgnoredSuccessfulTests = numberOfIgnoredSuccessfulTests;
	}

	public void setNumberOfTestsForStatus(String status, int numberOfTests) {
		numberOfTestsByStatus.put(status, Integer.valueOf(numberOfTests));
	}

	public int getNumberOfTestsForStatus(String status) {
		Integer i = numberOfTestsByStatus.get(status);
		return i == null ? 0 : i.intValue();
	}

	public Map<String, Integer> getNumberOfTestsByStatus() {
		return numberOfTestsByStatus;
	}

	public void setNumberOfTestsByStatus(Map<String, Integer> numberOfTestsByStatus) {
		this.numberOfTestsByStatus = numberOfTestsByStatus;
	}

	public static AludratestSuiteExecutionStatistics fromJSON(JSONObject object) {
		JsonConfig config = new JsonConfig();
		config.setRootClass(AludratestSuiteExecutionStatistics.class);
		return (AludratestSuiteExecutionStatistics) JSONSerializer.toJava(object, config);
	}

	private static AludratestSuiteExecutionStatistics fromSuiteNode(Element suiteNode, XPathFactory factory)
			throws XPathExpressionException {
		AludratestSuiteExecutionStatistics result = new AludratestSuiteExecutionStatistics();

		result.suiteName = XPathUtil.evalXPathAsString(suiteNode, "name/text()", factory);

		// use this when generating child elements
		// String tcPath = "testCases/testCase";
		String tcPath = ".//testCase";

		// count direct test cases
		result.setNumberOfTests(XPathUtil.evalXPathAsInt(suiteNode, "count(" + tcPath + ")", factory));
		result.setNumberOfFailedTests(
				XPathUtil.evalXPathAsInt(suiteNode, "count(" + tcPath + "[./status/text() != 'PASSED' and not(@ignored='true')])",
				factory));
		result.setNumberOfSuccessfulTests(XPathUtil.evalXPathAsInt(suiteNode,
				"count(" + tcPath + "[./status/text() = 'PASSED' and not(@ignored='true')])", factory));
		result.setNumberOfIgnoredTests(XPathUtil.evalXPathAsInt(suiteNode, "count(" + tcPath + "[@ignored='true'])", factory));
		result.setNumberOfIgnoredFailedTests(XPathUtil.evalXPathAsInt(suiteNode, "count(" + tcPath
				+ "[./status/text() != 'PASSED' and @ignored='true'])", factory));
		result.setNumberOfIgnoredSuccessfulTests(XPathUtil.evalXPathAsInt(suiteNode,
				"count(" + tcPath
				+ "[./status/text() = 'PASSED' and @ignored='true'])", factory));

		List<String> allStates = XPathUtil.evalXPathAsStringList(suiteNode, tcPath + "[not(@ignored)]/status", factory, false);

		for (String statusName : allStates) {
			result.setNumberOfTestsForStatus(
					statusName,
					XPathUtil.evalXPathAsInt(suiteNode, "count(" + tcPath + "[not(@ignored)][./status/text() = '" + statusName
							+ "'])", factory));
		}

		// add child values
		// NodeList childSuites = XPathUtil.evalXPathAsNodeList(suiteNode, "testSuites/testSuite", factory);
		// for (int i = 0; i < childSuites.getLength(); i++) {
		// result.childSuites.add(fromSuiteNode((Element) childSuites.item(i), factory));
		// }

		for (AludratestSuiteExecutionStatistics child : result.childSuites) {
			result.numberOfTests += child.numberOfTests;
			result.numberOfSuccessfulTests += child.numberOfSuccessfulTests;
			result.numberOfIgnoredTests += child.numberOfIgnoredTests;
			result.numberOfFailedTests += child.numberOfFailedTests;
			result.numberOfIgnoredTests += child.numberOfIgnoredTests;
			result.numberOfIgnoredSuccessfulTests += child.numberOfIgnoredSuccessfulTests;
			result.numberOfIgnoredFailedTests += child.numberOfIgnoredFailedTests;

			for (Map.Entry<String, Integer> entry : child.numberOfTestsByStatus.entrySet()) {
				if (!result.numberOfTestsByStatus.containsKey(entry.getKey())) {
					result.numberOfTestsByStatus.put(entry.getKey(), entry.getValue());
				}
				else {
					int cnt = result.numberOfTestsByStatus.get(entry.getKey()).intValue();
					cnt += entry.getValue().intValue();
					result.numberOfTestsByStatus.put(entry.getKey(), Integer.valueOf(cnt));
				}
			}
		}

		return result;
	}

	public static AludratestSuiteExecutionStatistics fromResultsXml(Document doc) {
		try {
			XPathFactory factory = XPathFactory.newInstance();
			return fromSuiteNode(doc.getDocumentElement(), factory);
		}
		catch (XPathExpressionException e) {
			throw new RuntimeException("Invalid XPath used", e);
		}
	}

}
