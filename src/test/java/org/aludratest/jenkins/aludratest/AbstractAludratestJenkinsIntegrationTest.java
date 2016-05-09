package org.aludratest.jenkins.aludratest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;

public abstract class AbstractAludratestJenkinsIntegrationTest {

	@Rule
	public JenkinsRule j = new JenkinsRule();

	protected final FreeStyleProject createTestProject(String resultXmlFile, String htmlLogPath) throws Exception {
		FreeStyleProject project = j.createFreeStyleProject("IntegrationTest");

		// write our result.xmls to temporary files
		File resFile1 = copyResourceToTempFile("result1.xml");
		File resFile2 = copyResourceToTempFile("result2.xml");

		// simulate creation of result.xml by copying it
		project.getBuildersList().add(new Shell("cp \"" + resFile1.getAbsolutePath().replace("\\", "/") + "\" ./result.xml"));
		project.getBuildersList().add(new Shell("cp \"" + resFile2.getAbsolutePath().replace("\\", "/") + "\" ./result2.xml"));

		// the standard publisher (object under test)
		project.getPublishersList().add(new AludratestStatisticsPublisher(resultXmlFile, htmlLogPath));

		return project;
	}

	private File copyResourceToTempFile(String resourceName) throws Exception {
		File tempFile = File.createTempFile("aludraTestJenkinsTest", "result.xml");
		InputStream in = getClass().getResourceAsStream(resourceName);
		FileOutputStream fos = new FileOutputStream(tempFile);
		try {
			IOUtils.copy(in, fos);
		}
		finally {
			IOUtils.closeQuietly(fos);
			IOUtils.closeQuietly(in);
		}

		tempFile.deleteOnExit();
		return tempFile;
	}

}
