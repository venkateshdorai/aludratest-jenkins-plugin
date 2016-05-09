package org.aludratest.jenkins.aludratest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import hudson.util.FormValidation;

public class AludratestStatisticsPublisherTest {

	@Test
	public void testConstructor() {
		AludratestStatisticsPublisher publisher = new AludratestStatisticsPublisher("test", "/123/4");
		assertEquals("test", publisher.getResultXmlFile());
		assertEquals("/123/4", publisher.getHtmlLogPath());
	}

	@Test
	public void testFormValidation() throws Exception {
		AludratestStatisticsPublisher publisher = new AludratestStatisticsPublisher("test", "/123/4");
		FormValidation fv = publisher.doCheckXmlFilePath("");
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		fv = publisher.doCheckXmlFilePath("test/1");
		assertEquals(FormValidation.Kind.OK, fv.kind);
	}

}
