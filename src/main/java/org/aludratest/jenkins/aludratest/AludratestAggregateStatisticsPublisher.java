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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.acegisecurity.AccessDeniedException;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ComboBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

public class AludratestAggregateStatisticsPublisher extends Recorder implements SimpleBuildStep, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

	private List<ProjectEntry> projectEntries = new ArrayList<ProjectEntry>();

	@SuppressWarnings("rawtypes")
	@Extension
	public static final class AludratestAggregateStatisticsPublisherDescriptor extends BuildStepDescriptor {

		public AludratestAggregateStatisticsPublisherDescriptor() {
			super();
		}

		@Override
		public String getDisplayName() {
			return "Aggregate AludraTest Report from other projects";
		}

		@Override
		public Class getT() {
			return Publisher.class;
		}

		@Override
		public boolean isApplicable(Class jobType) {
			return true;
		}

	}

	public static class ProjectEntry extends AbstractDescribableImpl<ProjectEntry> implements Serializable {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		// the project of which to get the statistics
		private String projectName;

		// the build of which to get the statistics, or null to use latest build
		private String buildNumber;

		// the group name to use for the statistics
		private String statisticsGroupName;

		@DataBoundConstructor
		public ProjectEntry(String projectName, String buildNumber, String statisticsGroupName) {
			super();
			this.projectName = projectName;
			this.buildNumber = buildNumber;
			this.statisticsGroupName = statisticsGroupName;
		}

		public String getProjectName() {
			return projectName;
		}

		public void setProjectName(String projectName) {
			this.projectName = projectName;
		}

		public String getBuildNumber() {
			return buildNumber;
		}

		public void setBuildNumber(String buildNumber) {
			this.buildNumber = buildNumber;
		}

		public String getStatisticsGroupName() {
			return statisticsGroupName;
		}

		public void setStatisticsGroupName(String statisticsGroupName) {
			this.statisticsGroupName = statisticsGroupName;
		}

		@Extension
		public static class DescriptorImpl extends Descriptor<ProjectEntry> {

			@Override
			public String getDisplayName() {
				return "";
			}

			public ComboBoxModel doFillProjectNameItems() {
				ComboBoxModel model = new ComboBoxModel();
				
				Jenkins jenkins = Jenkins.getInstance();
				if (jenkins != null) {
					for (String jn : jenkins.getJobNames()) {
						if (isApplicableForAggregation(jn)) {
							model.add(jn);
						}
					}
				}

				return model;
			}
		}
	}

	@DataBoundConstructor
	public AludratestAggregateStatisticsPublisher(ArrayList<ProjectEntry> projectEntries) {
		this.projectEntries = projectEntries;
	}

	public List<ProjectEntry> getProjectEntries() {
		return projectEntries;
	}

	public void setProjectEntries(List<ProjectEntry> projectEntries) {
		this.projectEntries = projectEntries;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
		return Collections.singletonList(new AludratestStatisticsAction(project));
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException,
			IOException {
		// check if there already is a file to append to, or if we must create a new one
		File fStatsFile = new File(run.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME);

		JSONObject rootObject;
		if (fStatsFile.isFile()) {
			rootObject = (JSONObject) JSONSerializer.toJSON(FileUtils.readFileToString(fStatsFile, "UTF-8"));
		}
		else {
			rootObject = new JSONObject();
		}

		for (ProjectEntry entry : projectEntries) {
			String statisticsGroupName = entry.getStatisticsGroupName();
			String projectName = entry.getProjectName();
			String buildNumber = entry.getBuildNumber();

			// check parameter validness
			if (statisticsGroupName == null || "".equals(statisticsGroupName)) {
				throw new AbortException(
						"Cannot aggregate AludraTest statistics from other project: 'Statistics group name' parameter is not set.");
			}
			if (projectName == null || "".equals(projectName)) {
				throw new AbortException(
						"Cannot aggregate AludraTest statistics from other project: 'Project name' parameter is not set.");
			}

			// project must exist!
			AbstractProject<?, ?> otherProject = null;
			try {
				Item item = run.getParent().getParent().getItem(projectName);
				// also catches null case
				if (!(item instanceof AbstractProject)) {
					throw new AbortException("Project " + projectName + " not found.");
				}
				otherProject = (AbstractProject<?, ?>) item;
			}
			catch (AccessDeniedException e) {
				throw new AbortException("Cannot access project " + projectName + " for READ. Please check your access rights.");
			}

			// project must have build with given number, or at least one build when number not set
			Run<?, ?> otherRun = null;
			if (buildNumber == null || "".equals(buildNumber)) {
				otherRun = otherProject.getLastBuild();
				while (otherRun != null && otherRun.isBuilding()) {
					otherRun = otherRun.getPreviousBuild();
				}
				if (otherRun == null) {
					throw new AbortException("The project " + projectName + " does not contain any finished builds.");
				}
			}
			else {
				try {
					int bn = Integer.parseInt(buildNumber);
					otherRun = otherProject.getBuildByNumber(bn);
					if (otherRun == null) {
						throw new AbortException("The project " + projectName + " does not contain a build #" + bn);
					}
				}
				catch (NumberFormatException e) {
					throw new AbortException("Invalid build number parameter: " + buildNumber);
				}
			}

			listener.getLogger().println(
					"Aggregating AludraTest statistics from project " + projectName + ", build #" + otherRun.getNumber());

			// read AludraTest statistics file from there
			File fOtherStatsFile = new File(otherRun.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME);
			if (!fOtherStatsFile.isFile()) {
				throw new AbortException("Build " + projectName + "#" + buildNumber
						+ " does not have generated AludraTest statistics");
			}

			// read into memory
			JSONObject statsData = (JSONObject) JSONSerializer.toJSON(FileUtils.readFileToString(fOtherStatsFile));

			// has HTML location?
			String htmlLogPath = statsData.optString(ScriptConstants.HTML_LOG_PATH_FIELD, null);

			// merge into existing structure
			AludratestSuiteExecutionStatistics globalStats = AludratestSuiteExecutionStatistics.fromJSON(statsData
					.getJSONObject(ScriptConstants.GLOBAL_STATS_FIELD));

			AludratestSuiteExecutionStatistics existingStats;
			if (rootObject.has(ScriptConstants.GLOBAL_STATS_FIELD)) {
				existingStats = AludratestSuiteExecutionStatistics.fromJSON(rootObject
						.getJSONObject(ScriptConstants.GLOBAL_STATS_FIELD));
			}
			else {
				existingStats = new AludratestSuiteExecutionStatistics();
				existingStats.setSuiteName("Root Suite");
			}

			// previous global stats become a child of existingStats
			globalStats.setSuiteName(statisticsGroupName);
			existingStats.getChildSuites().add(globalStats);

			// aggregate values into existing numbers
			existingStats.setNumberOfTests(existingStats.getNumberOfTests() + globalStats.getNumberOfTests());
			existingStats.setNumberOfFailedTests(existingStats.getNumberOfFailedTests() + globalStats.getNumberOfFailedTests());
			existingStats.setNumberOfSuccessfulTests(existingStats.getNumberOfSuccessfulTests()
					+ globalStats.getNumberOfSuccessfulTests());
			existingStats.setNumberOfIgnoredFailedTests(existingStats.getNumberOfIgnoredFailedTests()
					+ globalStats.getNumberOfIgnoredFailedTests());
			existingStats.setNumberOfIgnoredSuccessfulTests(existingStats.getNumberOfIgnoredSuccessfulTests()
					+ globalStats.getNumberOfIgnoredSuccessfulTests());
			existingStats
					.setNumberOfIgnoredTests(existingStats.getNumberOfIgnoredTests() + globalStats.getNumberOfIgnoredTests());

			Map<String, Integer> numberOfTestsByStatus = new HashMap<String, Integer>(existingStats.getNumberOfTestsByStatus());

			// build superset of keys
			Set<String> superKeys = new HashSet<String>(numberOfTestsByStatus.keySet());
			superKeys.addAll(globalStats.getNumberOfTestsByStatus().keySet());

			// merge add maps
			for (String key : superKeys) {
				Integer i1 = numberOfTestsByStatus.get(key);
				Integer i2 = globalStats.getNumberOfTestsByStatus().get(key);

				numberOfTestsByStatus.put(key,
						Integer.valueOf((i1 == null ? 0 : i1.intValue()) + (i2 == null ? 0 : i2.intValue())));
			}

			existingStats.setNumberOfTestsByStatus(numberOfTestsByStatus);
			rootObject.put(ScriptConstants.GLOBAL_STATS_FIELD, JSONSerializer.toJSON(existingStats));

			// now integrate single test results
			JSONObject singleTestStats = statsData.getJSONObject(ScriptConstants.SINGLE_TESTS_FIELD);
			JSONObject existingSingleTestStats;
			if (!rootObject.has(ScriptConstants.SINGLE_TESTS_FIELD)) {
				existingSingleTestStats = new JSONObject();
			}
			else {
				existingSingleTestStats = rootObject.getJSONObject(ScriptConstants.SINGLE_TESTS_FIELD);
			}

			Iterator<?> iter = singleTestStats.keys();
			while (iter.hasNext()) {
				String testCaseName = iter.next().toString();
				JSONObject obj = singleTestStats.getJSONObject(testCaseName);
				if (htmlLogPath != null) {
					obj.put(ScriptConstants.HTML_LOG_PATH_FIELD, otherRun.getUrl() + htmlLogPath);
				}

				// do not overwrite existing; fail instead (otherwise, numbers would not be correct)
				if (existingSingleTestStats.containsKey(testCaseName)) {
					throw new AbortException("The test case " + testCaseName
							+ " has already been loaded into aggregation. Aggregation cannot be performed.");
				}

				existingSingleTestStats.put(testCaseName, obj);
			}
			rootObject.put(ScriptConstants.SINGLE_TESTS_FIELD, existingSingleTestStats);
		}

		// and write back to file
		FileUtils.write(fStatsFile, rootObject.toString());

		// enrich this build with the report
		run.addAction(new AludratestBuildStatisticsAction(run));
	}

	private static boolean isApplicableForAggregation(String jobName) {
		Jenkins jenkins = Jenkins.getInstance();
		Item item = jenkins.getItem(jobName);
		if (!(item instanceof AbstractProject<?, ?>)) {
			return false;
		}

		AbstractProject<?, ?> project = (AbstractProject<?, ?>) item;

		// job must have the AludraTest Statistics Action enabled
		return !project.getActions(AludratestStatisticsAction.class).isEmpty();
	}

}
;