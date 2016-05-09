package org.aludratest.jenkins.aludratest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import net.sf.json.JSONObject;

public class AludratestStatisticsPublisherIntegrationTest extends AbstractAludratestJenkinsIntegrationTest {

	private void assertStringListEntryMatches(List<String> logEntries, String pattern) {
		Pattern p = Pattern.compile(pattern);
		for (String entry : logEntries) {
			if (p.matcher(entry).matches()) {
				return;
			}
		}
		fail("No log entry found matching expected RegExp pattern " + pattern + " in list " + logEntries);
	}

	@Test
	public void testNonExistingFile() throws Exception {
		FreeStyleProject project = createTestProject("someTypoInResult.xml", "artifact/target/result/html");
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		assertEquals(Result.FAILURE, build.getResult());
		assertStringListEntryMatches(build.getLog(300), ".*The AludraTest XML file someTypoInResult.xml does not exist.*");
	}

	@Test
	public void testPositiveCase() throws Exception {
		FreeStyleProject project = createTestProject("result.xml", "artifact/target/result/html");
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		assertEquals(Result.SUCCESS, build.getResult());

		// an aludraTest.json file must have been created
		File jsonFile = new File(build.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME);
		assertTrue(jsonFile.isFile());

		// read file as JSON
		String jsonContents = IOUtils.toString(jsonFile.toURI());
		JSONObject object = JSONObject.fromObject(jsonContents);

		// validate contents
		assertEquals("artifact/target/result/html", object.getString(ScriptConstants.HTML_LOG_PATH_FIELD));
		JSONObject globalStats = object.getJSONObject(ScriptConstants.GLOBAL_STATS_FIELD);
		assertEquals(6, globalStats.getInt("numberOfTests"));
		assertEquals(4, globalStats.getJSONObject("numberOfTestsByStatus").getInt("PASSED"));
		assertEquals(4, globalStats.getInt("numberOfSuccessfulTests"));
		assertEquals(1, globalStats.getInt("numberOfFailedTests"));
		assertEquals(1, globalStats.getInt("numberOfIgnoredSuccessfulTests"));
		assertEquals(0, globalStats.getInt("numberOfIgnoredFailedTests"));
		assertEquals(1, globalStats.getJSONObject("numberOfTestsByStatus").getInt("FAILEDAUTOMATION"));

		JSONObject singleStats = object.getJSONObject(ScriptConstants.SINGLE_TESTS_FIELD);
		assertEquals(6, singleStats.size());

		// all test cases must have their own entry
		JSONObject testCaseData = singleStats.getJSONObject("suite1.test1.config1");
		assertEquals("PASSED", testCaseData.getString("status"));

		// it's up to the implementation if to include "ignored" field at all when false
		assertFalse(testCaseData.optBoolean("ignored", false));
		testCaseData = singleStats.getJSONObject("suite2.test1.config1");
		assertEquals("PASSED", testCaseData.getString("status"));
		assertTrue(testCaseData.getBoolean("ignored"));
		testCaseData = singleStats.getJSONObject("suite3.test1.config1");
		assertEquals("PASSED", testCaseData.getString("status"));
		assertFalse(testCaseData.optBoolean("ignored", false));
		testCaseData = singleStats.getJSONObject("suite4.test1.config1");
		assertEquals("PASSED", testCaseData.getString("status"));
		assertFalse(testCaseData.optBoolean("ignored", false));
		testCaseData = singleStats.getJSONObject("suite5.test1.config1");
		assertEquals("FAILEDAUTOMATION", testCaseData.getString("status"));
		assertFalse(testCaseData.optBoolean("ignored", false));
		testCaseData = singleStats.getJSONObject("suite5.test1.config2");
		assertEquals("PASSED", testCaseData.getString("status"));
		assertFalse(testCaseData.optBoolean("ignored", false));
	}
}
