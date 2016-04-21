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
import java.util.List;

import org.apache.commons.io.FileUtils;

import hudson.Functions;
import hudson.model.Run;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

/**
 * Report for a single build.
 * 
 * @author falbrech
 * 
 */
public class AludratestBuildStatisticsReport {

	private Run<?, ?> build;

	public AludratestBuildStatisticsReport(Run<?, ?> build) {
		this.build = build;
	}

	public Run<?, ?> getBuild() {
		return build;
	}

	public String getTitle() {
		return "AludraTest Execution Report";
	}

	public String getResURL() {
		Jenkins jenkins = Jenkins.getInstance();
		if (jenkins == null) {
			return null;
		}

		return jenkins.getRootUrl() + Functions.getResourcePath();
	}

	public String generateJsData(String compareBuildId) {
		StringBuilder sb = new StringBuilder();
		sb.append(generateJsData(build, "current"));

		if (compareBuildId != null && !"".equals(compareBuildId)) {
			Run<?, ?> compareBuild = build.getParent().getBuild(compareBuildId);
			if (compareBuild != null) {
				sb.append(generateJsData(compareBuild, "compare"));
			}
		}

		return sb.toString();
	}

	private String generateJsData(Run<?, ?> build, String varNamePrefix) {
		// load data for current and compare build
		File statsFile = new File(build.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME);
		if (!statsFile.isFile()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();

		try {
			JSONObject object = (JSONObject) JSONSerializer.toJSON(FileUtils.readFileToString(statsFile, "UTF-8"));
			sb.append("var ").append(varNamePrefix).append("Statistics = ");
			sb.append(object.getJSONObject(ScriptConstants.GLOBAL_STATS_FIELD).toString()).append(";\n");

			sb.append("var ").append(varNamePrefix).append("TestCaseResults = ");
			sb.append(object.getJSONObject(ScriptConstants.SINGLE_TESTS_FIELD).toString()).append(";\n");
			
			if (object.has(ScriptConstants.HTML_LOG_PATH_FIELD)) {
				sb.append("var ").append(varNamePrefix).append("HtmlLogPath = '");
				sb.append(build.getUrl()).append(object.getString(ScriptConstants.HTML_LOG_PATH_FIELD)).append("';\n");
			}
		}
		catch (IOException e) {
			// TODO log error
		}

		return sb.toString();
	}

	public List<String> getPreviousBuildIds() {
		List<String> result = new ArrayList<String>();
		Run<?, ?> run = build.getPreviousBuild();
		while (run != null) {
			File f = new File(run.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME);
			if (f.isFile()) {
				result.add(run.getId());
			}
			run = run.getPreviousBuild();
		}

		return result;
	}

	public String getBuildNumber(String buildId) {
		if (buildId == null) {
			return null;
		}
		Run<?, ?> run = build.getParent().getBuild(buildId);
		return run == null ? "???" : String.valueOf(run.getNumber());
	}

}
