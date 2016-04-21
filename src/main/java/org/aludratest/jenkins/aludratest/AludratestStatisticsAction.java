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

import org.kohsuke.stapler.StaplerProxy;

import hudson.model.AbstractProject;
import hudson.model.Action;
import jenkins.model.Jenkins;

public class AludratestStatisticsAction implements Action, StaplerProxy {

	private AbstractProject<?, ?> project;

	public AludratestStatisticsAction(AbstractProject<?, ?> project) {
		this.project = project;
	}

	@Override
	public String getIconFileName() {
		return Jenkins.RESOURCE_PATH + "/plugin/aludratest/icons/aludratest_24x24.png";
	}

	@Override
	public String getDisplayName() {
		return "AludraTest Execution Statistics";
	}

	@Override
	public String getUrlName() {
		return "aludratest";
	}

	@Override
	public Object getTarget() {
		return new AludratestProjectStatisticsReport(project);
	}

}
