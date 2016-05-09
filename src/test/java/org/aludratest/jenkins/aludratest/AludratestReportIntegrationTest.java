package org.aludratest.jenkins.aludratest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

public class AludratestReportIntegrationTest extends AbstractAludratestJenkinsIntegrationTest {

	private FreeStyleProject prepareProject() throws Exception {
		FreeStyleProject project = createTestProject("./result.xml", "some/html/path");
		FreeStyleBuild build1 = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build1.getResult());

		// reconfigure publisher to now use result2.xml
		project.getPublishersList().clear();
		project.getPublishersList().add(new AludratestStatisticsPublisher("./result2.xml", "some/other/html/path"));

		FreeStyleBuild build2 = project.scheduleBuild2(0).get();
		assertEquals(Result.SUCCESS, build2.getResult());
		
		assertTrue(new File(build2.getRootDir(), AludratestStatisticsPublisher.STATISTICS_FILE_NAME).isFile());

		return project;
	}

	private void assertDataRowEquals(List<HtmlElement> columns, String... expectedContent) {
		for (int i = 0; i < expectedContent.length; i++) {
			assertEquals(expectedContent[i], columns.get(i).getTextContent().trim());
		}
	}

	@Test
	public void testPositiveCase() throws Exception {
		prepareProject();

		// now, generate the report, and check chart
		WebClient client = j.createWebClient();
		HtmlPage page = client.goTo("job/IntegrationTest");

		// AludraTest link must be available
		assertEquals(1, page.getByXPath("//a[contains(@href, '/aludratest') and @class='task-link']").size());

		// go to AludraTest page
		page = client.goTo("job/IntegrationTest/aludratest");

		// make basic checks with the chart
		List<?> groupsGroup = page.getByXPath(
				"//g[contains(@class, 'nv-stackedAreaChart')]//g[contains(@class, 'nv-scatterWrap')]//g[@class='nv-groups']");
		assertEquals(1, groupsGroup.size());

		HtmlElement elem = (HtmlElement) groupsGroup.get(0);

		// 8 states must be present in chart
		assertEquals(8, elem.getElementsByTagName("g").size());

		// first state should have two data points (like all states)
		assertEquals(2, elem.getElementsByTagName("g").get(0).getElementsByTagName("path").size());

		// check data table
		HtmlElement dataTable = (HtmlElement) page.getByXPath("//table[@id='test_cases_table']").get(0);
		HtmlElement dataTableBody = dataTable.getElementsByTagName("tbody").get(0);

		// should initially be sorted by name, so we can compare order
		List<HtmlElement> rows = dataTableBody.getElementsByTagName("tr");
		assertDataRowEquals(rows.get(0).getElementsByTagName("td"), "suite1. test1. config1", "2", "1", "0.00%", "1", "0", "0",
				"0", "0");
		assertDataRowEquals(rows.get(1).getElementsByTagName("td"), "suite2. test1. config1", "2", "0", "0.00%", "0", "0", "0",
				"0", "0");
		assertDataRowEquals(rows.get(2).getElementsByTagName("td"), "suite3. test1. config1", "2", "0", "0.00%", "0", "0", "0",
				"0", "0");
		assertDataRowEquals(rows.get(3).getElementsByTagName("td"), "suite4. test1. config1", "2", "0", "0.00%", "0", "0", "0",
				"0", "0");
		assertDataRowEquals(rows.get(4).getElementsByTagName("td"), "suite5. test1. config1", "2", "1", "50.00%", "0", "0", "0",
				"1", "0");
		assertDataRowEquals(rows.get(5).getElementsByTagName("td"), "suite5. test1. config2", "2", "0", "0.00%", "0", "0", "0",
				"0", "0");

		// go to build #1 and check link
		page = client.goTo("job/IntegrationTest/1");
		assertEquals(1, page.getByXPath("//a[contains(@href, '/aludratest') and @class='task-link']").size());

		// go to pie chart view
		page = client.goTo("job/IntegrationTest/1/aludratest");

		// check slices of pie chart (must be 3: PASSED, FAILEDAUTOMATION, IGNORED_PASSED)
		elem = (HtmlElement) page.getByXPath("//svg[@id='chart_pie_current']//g[@class='nv-pie']").get(0);
		assertEquals(3, elem.getElementsByTagName("g").size());

		// check datatable
		dataTable = (HtmlElement) page.getByXPath("//table[@id='chart_pie_datatable']").get(0);
		dataTableBody = dataTable.getElementsByTagName("tbody").get(0);

		// should have three rows
		assertEquals(3, dataTableBody.getElementsByTagName("tr").size());

		Map<String, String> expectedValues = new HashMap<>();
		expectedValues.put("PASSED", "4");
		expectedValues.put("FAILEDAUTOMATION", "1");
		expectedValues.put("IGNORED_PASSED", "1");

		for (int i = 0; i < 3; i++) {
			List<HtmlElement> cols = dataTableBody.getElementsByTagName("tr").get(i).getElementsByTagName("td");
			String key = cols.get(0).getTextContent().trim();
			String value = cols.get(1).getTextContent().trim();
			assertEquals(expectedValues.get(key), value);
		}

		// check test case list
		dataTable = (HtmlElement) page.getByXPath("//table[@id='testCases_table']").get(0);
		dataTableBody = dataTable.getElementsByTagName("tbody").get(0);

		assertDataRowEquals(dataTableBody.getElementsByTagName("tr").get(0).getElementsByTagName("td"), "suite1. test1. config1",
				"PASSED", "No", "Current Log");
		assertDataRowEquals(dataTableBody.getElementsByTagName("tr").get(1).getElementsByTagName("td"), "suite2. test1. config1",
				"PASSED", "Yes", "Current Log");
		assertDataRowEquals(dataTableBody.getElementsByTagName("tr").get(2).getElementsByTagName("td"), "suite3. test1. config1",
				"PASSED", "No", "Current Log");
		assertDataRowEquals(dataTableBody.getElementsByTagName("tr").get(3).getElementsByTagName("td"), "suite4. test1. config1",
				"PASSED", "No", "Current Log");
		assertDataRowEquals(dataTableBody.getElementsByTagName("tr").get(4).getElementsByTagName("td"), "suite5. test1. config1",
				"FAILEDAUTOMATION", "No", "Current Log");
		assertDataRowEquals(dataTableBody.getElementsByTagName("tr").get(5).getElementsByTagName("td"), "suite5. test1. config2",
				"PASSED", "No", "Current Log");
		
		// check correct CSS in 5th row
		HtmlElement cell = dataTableBody.getElementsByTagName("tr").get(4).getElementsByTagName("td").get(1);
		HtmlElement span = cell.getElementsByTagName("span").get(0);
		assertEquals("aludratest-status-FAILEDAUTOMATION", span.getAttribute("class"));
		
		// examine log link in second row
		cell = dataTableBody.getElementsByTagName("tr").get(1).getElementsByTagName("td").get(3);
		HtmlElement link = cell.getElementsByTagName("a").get(0);
		assertEquals("/jenkins/job/IntegrationTest/1/some/html/path/suite2/test1/config1.html",
				link.getAttribute("href"));
		assertEquals("_blank", link.getAttribute("target"));
	}
}
