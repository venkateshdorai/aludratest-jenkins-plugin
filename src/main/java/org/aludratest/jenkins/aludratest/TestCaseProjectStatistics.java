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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The statistics for a single test case throughout all builds. Lists in objects of this class refer to the lists of build numbers
 * of the corresponding project report.
 * 
 * @author falbrech
 *
 */
public class TestCaseProjectStatistics {

	private int executionCount;

	private int failureCount;

	private Map<String, Integer> countPerStatus = new LinkedHashMap<>();

	private List<SingleTestResult> results = new ArrayList<>();

	public void addNoRun() {
		results.add(null);
	}

	public void addExecutionResult(SingleTestResult result) {
		executionCount++;
		failureCount += 1 - result.getSuccess();

		Integer i = countPerStatus.get(result.getStatus());
		if (i == null) {
			i = Integer.valueOf(0);
		}
		countPerStatus.put(result.getStatus(), Integer.valueOf(i.intValue() + 1));
		results.add(result);
	}

	public int getExecutionCount() {
		return executionCount;
	}

	public int getFailureCount() {
		return failureCount;
	}

	public Map<String, Integer> getCountPerStatus() {
		return countPerStatus;
	}

	public List<SingleTestResult> getResults() {
		return Collections.unmodifiableList(results);
	}

}
