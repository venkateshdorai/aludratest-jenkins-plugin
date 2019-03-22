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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

public class AludratestStatisticsPublisher extends Recorder implements SimpleBuildStep {

	public static final String STATISTICS_FILE_NAME = "aludratestStats.json";

	private String resultXmlFile;

	private String htmlLogPath;

	@SuppressWarnings("rawtypes")
	@Extension
	public static final class AludratestPublisherDescriptor extends BuildStepDescriptor {

		public AludratestPublisherDescriptor() {
			super();
		}

		@Override
		public String getDisplayName() {
			// TODO externalize
			return "Generate AludraTest Report";
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

	@DataBoundConstructor
	public AludratestStatisticsPublisher(String resultXmlFile, String htmlLogPath) {
		this.resultXmlFile = resultXmlFile;
		this.htmlLogPath = htmlLogPath;
	}

	public String getResultXmlFile() {
		return resultXmlFile;
	}

	public String getHtmlLogPath() {
		return htmlLogPath;
	}

	@Override
	public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
		return Collections.singletonList(new AludratestStatisticsAction(project));
	}

	@Override
	public AludratestPublisherDescriptor getDescriptor() {
		Jenkins jenkins = Jenkins.getInstance();
		return jenkins.getDescriptorByType(AludratestPublisherDescriptor.class);
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public FormValidation doCheckXmlFilePath(@QueryParameter String value) throws IOException, ServletException {
		if (value.length() == 0)
			return FormValidation.error("Please set a path to the XML log file");
		return FormValidation.ok();
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException,
			IOException {
		listener.getLogger().println("Gathering AludraTest statistics");

		// find result XML file
		FilePath resultsFile = workspace.child(resultXmlFile);
		if (!resultsFile.exists()) {
			throw new AbortException("The AludraTest XML file " + resultXmlFile + " does not exist.");
		}

		// if file size is 0, skip step
		if (resultsFile.length() == 0) {
			listener.getLogger().println("AludraTest XML file is empty, skipping statistics gathering");
			return;
		}

		// extract current statistics from XML file
		InputStream in = null;
		AludratestSuiteExecutionStatistics currentStats;
		Map<String, SingleTestResult> singleTestStats;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();

			in = resultsFile.read();
			Document doc = builder.parse(in);

			currentStats = AludratestSuiteExecutionStatistics.fromResultsXml(doc);
			singleTestStats = SingleTestResult.fromResultsXml(doc);
		}
		catch (ParserConfigurationException e) {
			throw new IOException("Internal exception when reading AludraTest XML file", e);
		}
		catch (SAXException e) {
			throw new IOException("Could not parse AludraTest XML file", e);
		}
		finally {
			IOUtils.closeQuietly(in);
		}

		// write to JSON here
		File statsOutFile = new File(run.getRootDir(), STATISTICS_FILE_NAME);
		FileOutputStream fos = new FileOutputStream(statsOutFile);
		OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");

		JSONObject obj = new JSONObject();
		obj.put(ScriptConstants.GLOBAL_STATS_FIELD, JSONSerializer.toJSON(currentStats));
		// TODO "customHtmlLogPath" is also included in JSON, which should be "transient".
		obj.put(ScriptConstants.SINGLE_TESTS_FIELD, JSONSerializer.toJSON(singleTestStats));
		if (htmlLogPath != null && !"".equals(htmlLogPath)) {
			obj.put(ScriptConstants.HTML_LOG_PATH_FIELD, htmlLogPath);
		}

		try {
			writer.write(obj.toString());
		}
		finally {
			IOUtils.closeQuietly(writer);
			IOUtils.closeQuietly(fos);
		}

		run.addAction(new AludratestBuildStatisticsAction(run));
		
		Job<?, ?> job = run.getParent();
		if (job.getActions(AludratestStatisticsAction.class).isEmpty()) {
			job.addAction(new AludratestStatisticsAction(job));
		}
		
	}

}
