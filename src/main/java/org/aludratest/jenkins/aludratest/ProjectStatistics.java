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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONObject;

/**
 * An "uncompressed" (partly redundant) view on the project's AludraTest statistics. Suitable for JSON serialization.
 * 
 * @author falbrech
 *
 */
public class ProjectStatistics {

	private List<Integer> buildNumbers = new ArrayList<>();

	private List<String> buildLabels = new ArrayList<>();

	private List<AludratestSuiteExecutionStatistics> globalStatistics = new ArrayList<>();

	private Map<String, TestCaseProjectStatistics> testCaseStatistics = new LinkedHashMap<>();

	public void addBuildData(int buildNumber, String buildLabel, JSONObject object) {
		buildNumbers.add(Integer.valueOf(buildNumber));
		buildLabels.add(buildLabel);

		globalStatistics
				.add(AludratestSuiteExecutionStatistics.fromJSON(object.getJSONObject(ScriptConstants.GLOBAL_STATS_FIELD)));

		JSONObject singleTestStats = object.getJSONObject(ScriptConstants.SINGLE_TESTS_FIELD);
		Iterator<?> iter = singleTestStats.keys();

		Set<String> runTests = new HashSet<>();

		while (iter.hasNext()) {
			String testCaseName = iter.next().toString();
			SingleTestResult result = SingleTestResult.fromJSON(singleTestStats.getJSONObject(testCaseName));

			runTests.add(testCaseName);
			TestCaseProjectStatistics tstats = testCaseStatistics.get(testCaseName);
			if (tstats == null) {
				testCaseStatistics.put(testCaseName, tstats = new TestCaseProjectStatistics());
				// fill "no run"s up to here
				for (int i = 0; i < globalStatistics.size() - 1; i++) {
					tstats.addNoRun();
				}

			}
			tstats.addExecutionResult(result);
		}

		Set<String> noRunTests = new HashSet<>(testCaseStatistics.keySet());
		noRunTests.removeAll(runTests);

		for (String testCaseName : noRunTests) {
			TestCaseProjectStatistics tstats = testCaseStatistics.get(testCaseName);
			// CANNOT be null, but just to be sure
			if (tstats != null) {
				tstats.addNoRun();
			}
		}
	}

	public List<Integer> getBuildNumbers() {
		return Collections.unmodifiableList(buildNumbers);
	}

	public List<String> getBuildLabels() {
		return Collections.unmodifiableList(buildLabels);
	}

	public List<AludratestSuiteExecutionStatistics> getGlobalStatistics() {
		return Collections.unmodifiableList(globalStatistics);
	}

	public Map<String, TestCaseProjectStatistics> getTestCaseStatistics() {
		return Collections.unmodifiableMap(testCaseStatistics);
	}

}
