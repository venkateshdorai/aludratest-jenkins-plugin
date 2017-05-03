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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import hudson.Functions;
import hudson.model.AbstractProject;
import hudson.model.Run;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;
import net.sf.json.util.PropertyFilter;

public class AludratestProjectStatisticsReport {

	private AbstractProject<?, ?> project;

	private ProjectStatistics cachedStatistics;

	public AludratestProjectStatisticsReport(AbstractProject<?, ?> project) {
		this.project = project;
	}

	public AbstractProject<?, ?> getProject() {
		return project;
	}

	public String getResURL() {
		Jenkins jenkins = Jenkins.getInstance();
		return jenkins.getRootUrl() + Functions.getResourcePath();
	}

	public String getDataVars(String fromBuildNumber, String toBuildNumber) {
		cacheStatistics(fromBuildNumber, toBuildNumber);

		StringBuilder sbVars = new StringBuilder();

		sbVars.append("var projectStats = ");
		JsonConfig config = new JsonConfig();
		config.setJsonPropertyFilter(new PropertyFilter() {
			@Override
			public boolean apply(Object source, String name, Object value) {
				if ("ignored".equals(name)) {
					return Boolean.FALSE.equals(value);
				}
				if ("ignoredReason".equals(name)) {
					return value == null || "".equals(value);
				}

				return false;
			}
		});
		config.setJavascriptCompliant(true);
		sbVars.append(JSONSerializer.toJSON(cachedStatistics, config).toString());
		sbVars.append(";\n");

		// build a map for the build name -> build description info
		Map<String, String> buildDescriptions = new LinkedHashMap<>();

		List<Integer> numbers = cachedStatistics.getBuildNumbers();
		List<String> labels = cachedStatistics.getBuildLabels();

		for (int i = 0; i < numbers.size(); i++) {
			String desc = project.getBuildByNumber(numbers.get(i).intValue()).getDescription();
			if (desc != null && !"".equals(desc)) {
				buildDescriptions.put(labels.get(i), desc);
			}
		}

		sbVars.append("var buildDescriptions = ");
		sbVars.append(JSONSerializer.toJSON(buildDescriptions));
		sbVars.append(";\n");

		return sbVars.toString();
	}

	public NavigationBean getNavBean() {
		return new NavigationBean(project);
	}

	public String doJson(@QueryParameter(required = false) String fromBuild, @QueryParameter(required = false) String toBuild) {
		cacheStatistics(fromBuild, toBuild);
		return JSONSerializer.toJSON(cachedStatistics).toString();
	}

	public String doCsv(@QueryParameter(required = false) String fromBuild, @QueryParameter(required = false) String toBuild) {
		cacheStatistics(fromBuild, toBuild);

		StringBuilder sb = new StringBuilder();

		// print header
		sb.append("Build Number;Build Label;Test Case;Execution Result;Success;Ignored;Reason for Ignore\n");

		for (Map.Entry<String, TestCaseProjectStatistics> entry : cachedStatistics.getTestCaseStatistics().entrySet()) {
			TestCaseProjectStatistics stats = entry.getValue();
			List<SingleTestResult> results = stats.getResults();
			for (int i = 0; i < results.size(); i++) {
				if (results.get(i) != null) {
					sb.append(cachedStatistics.getBuildNumbers().get(i)).append(";");
					sb.append(cachedStatistics.getBuildLabels().get(i)).append(";");
					sb.append(entry.getKey()).append(";");
					sb.append(results.get(i).getStatus()).append(";");
					sb.append(results.get(i).getSuccess()).append(";");
					sb.append(results.get(i).isIgnored() ? "1" : "0").append(";");
					if (results.get(i).isIgnored() && results.get(i).getIgnoredReason() != null) {
						sb.append(results.get(i).getIgnoredReason());
					}
					sb.append("\n");
				}
			}
		}

		return sb.toString();
	}

	private synchronized void cacheStatistics(String fromBuildNumber, String toBuildNumber) {
		cachedStatistics = new ProjectStatistics();

		// check if range (or at least start) is given
		int startBuildNo = -1;
		int endBuildNo = -1;
		if (fromBuildNumber != null && !"".equals(fromBuildNumber)) {
			try {
				startBuildNo = Integer.parseInt(fromBuildNumber);
				if (toBuildNumber != null && !"".equals(toBuildNumber)) {
					endBuildNo = Integer.parseInt(toBuildNumber);
				}

				if (startBuildNo < 0) {
					// relative mode: Find last N builds
					Run<?, ?> build = project.getLastBuild();
					int buildCount = 0;
					int targetBuildCount = startBuildNo * -1;
					while (build != null && buildCount < targetBuildCount) {
						if (new File(build.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME).isFile()) {
							buildCount++;
						}
						startBuildNo = build.getNumber();
						build = build.getPreviousBuild();
					}

					// no toBuild supported then
					endBuildNo = -1;
				}

			}
			catch (NumberFormatException e) {
				startBuildNo = endBuildNo = -1;
			}
		}

		// iterate over all builds having a stats file
		Run<?, ?> build = startBuildNo == -1 ? project.getFirstBuild() : project.getBuildByNumber(startBuildNo);
		if (build == null) {
			// no fallback here, no caching - empty results
			return;
		}

		// optimized, lengthy code to parallelize String -> JSON parsing
		// useful for MANY builds with HUGE amount of test cases
		List<Callable<Void>> runnables = new ArrayList<>();
		final Map<Integer, JSONObject> parsedObjects = new ConcurrentHashMap<>();

		while (build != null && (endBuildNo == -1 || build.getNumber() <= endBuildNo)) {
			final File statsFile = new File(build.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME);
			if (statsFile.isFile()) {
				final int buildNumber = build.getNumber();
				runnables.add(new Callable<Void>() {
					@Override
					public Void call() {
						try {
							JSONObject o = (JSONObject) JSONSerializer.toJSON(FileUtils.readFileToString(statsFile, "UTF-8"));
							parsedObjects.put(Integer.valueOf(buildNumber), o);
						}
						catch (IOException e) {
							// TODO log
						}
						return null;
					}
				});
			}

			build = build.getNextBuild();
		}

		if (!runnables.isEmpty()) {
			ExecutorService svc = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
			try {
				svc.invokeAll(runnables);
				svc.shutdown();
				if (!svc.awaitTermination(5, TimeUnit.MINUTES)) {
					// took too long...
					// TODO handle somehow
				}
			}
			catch (InterruptedException e) {
				return;
			}
		}

		List<Integer> keys = new ArrayList<>(parsedObjects.keySet());
		Collections.sort(keys);

		for (Integer buildNumber : keys) {
			// check if there is a display name for a build; otherwise, use # + number
			Run<?, ?> b = project.getBuildByNumber(buildNumber.intValue());
			String dn = (b != null ? b.getDisplayName() : null);
			if (dn == null) {
				dn = "#" + buildNumber;
			}
			cachedStatistics.addBuildData(buildNumber.intValue(), dn, parsedObjects.get(buildNumber));
		}
	}

	public static class NavigationBean {

		private AbstractProject<?, ?> project;

		public NavigationBean(AbstractProject<?, ?> project) {
			this.project = project;
		}

		@JavaScriptMethod
		public String getBuildUrl(int buildNumber) {
			return project.getBuildByNumber(buildNumber).getUrl() + "aludratest/";
		}

		/**
		 * Used by JavaScript AJAX requests to query the full log path for a given test case and build number.
		 * 
		 * @param testCaseName
		 *            Name of the test case.
		 * @param buildNumber
		 *            Number of the build.
		 * 
		 * @return The full URL to the HTML log of the test case in the given build, or <code>null</code> if no log can be
		 *         provided for this test case / build combination.
		 * 
		 * @throws IOException
		 *             If the statistics for the given build could not be read.
		 */
		@JavaScriptMethod
		public String getTestCaseLogPath(String testCaseName, int buildNumber) throws IOException {
			Run<?, ?> build = project.getBuildByNumber(buildNumber);
			if (build == null) {
				return null;
			}

			File statsFile = new File(build.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME);
			if (!statsFile.isFile()) {
				return null;
			}

			JSONObject object = (JSONObject) JSONSerializer.toJSON(FileUtils.readFileToString(statsFile, "UTF-8"));

			// first, check for an HTML log path at the test case itself
			String shortLogPath = null;
			if (object.has(ScriptConstants.SINGLE_TESTS_FIELD)) {
				JSONObject singleTestStats = object.getJSONObject(ScriptConstants.SINGLE_TESTS_FIELD);
				if (singleTestStats.has(testCaseName)) {
					JSONObject tcStats = singleTestStats.getJSONObject(testCaseName);
					if (tcStats.has(ScriptConstants.HTML_LOG_PATH_FIELD)) {
						shortLogPath = tcStats.getString(ScriptConstants.HTML_LOG_PATH_FIELD);
					}
				}
			}

			// now, check for a global log path
			if (shortLogPath == null && object.has(ScriptConstants.HTML_LOG_PATH_FIELD)) {
				shortLogPath = object.getString(ScriptConstants.HTML_LOG_PATH_FIELD);
			}

			if (shortLogPath == null) {
				// no log path available
				return null;
			}

			Jenkins jenkins = Jenkins.getInstance();
			String url = jenkins.getRootUrl();
			if (!url.endsWith("/")) {
				url += "/";
			}
			// if we are not an aggregated job, we have to prefix the log path with the job URL (dirty check)
			if (!shortLogPath.startsWith("job/") && !shortLogPath.startsWith("/job/")) {
				shortLogPath = build.getUrl() + shortLogPath;
			}

			url += shortLogPath;
			if (!url.endsWith("/")) {
				url += "/";
			}
			url += testCaseName.replace('.', '/') + ".html";

			return url;
		}


	}

}
