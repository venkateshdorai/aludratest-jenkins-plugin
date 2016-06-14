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
var allChangeDescriptions = [
	"PASSED in both builds",
	"Back to PASSED, no longer failing",
	"Failed before, still failing",
	"PASSED before, now failing",
	"Not present in previous build"
];

var failureChangeDescription = "{0} before, now {1}";

// provide a simple format function
if (!String.format) {
	String.format = function(format) {
		var args = Array.prototype.slice.call(arguments, 1);
		return format.replace(/{(\d+)}/g, function(match, number) {
			return typeof args[number] != 'undefined' ? args[number] : match;
		});
	};
}

function buildTestCaseTable(compare) {
	compare = compare !== '';

	var tableData = new Array();
	
	var currentHtmlLocation;
	var compareHtmlLocation;
	if (typeof(currentHtmlLogPath) !== 'undefined') {
		currentHtmlLocation = rootURL + "/" + currentHtmlLogPath;
	}
	if (typeof(compareHtmlLogPath) !== 'undefined') {
		compareHtmlLocation = rootURL + "/" + compareHtmlLogPath;
	}
	
	var i = 0;
	
	var includeCurrentLogColumn = false;
	var includeCompareLogColumn = false;
	
	jQuery.each(currentTestCaseResults, function(key, value) { 
		tableData[i] = [ key.replace(/\./g, ". ") ];
		var status = value.ignored == 1 ? ("IGNORED_" + (value.status === "PASSED" ? "PASSED" : "FAILED")) : value.status;
		tableData[i].push("<span class='aludratest-status-" + status + "'>" + value.status + "</span>");
		if (value.ignored == 1) {
		    if (typeof(value.ignoredReason) !== 'undefined') {
		    	tableData[i].push("<span title='" + value.ignoredReason + "'>Yes</span>");
		    }
		    else {
				tableData[i].push("Yes");
			}
		}
		else {
			tableData[i].push("No");
		}
		
		var compareObj;
		if (compare) {
			compareObj = compareTestCaseResults[key];
			if (typeof(compareObj) !== 'undefined') {
				var changeDescription = allChangeDescriptions[0];
				if (value.success == 1 && compareObj.success == 0) {
					changeDescription = allChangeDescriptions[1];
				}
				if (value.success == 0) {
					if (compareObj.success == 0) {
						// check if error state has changed
						if (value.status !== compareObj.status) {
							changeDescription = String.format(failureChangeDescription, compareObj.status, value.status);
						}
						else {
							changeDescription = allChangeDescriptions[2];
						}
					}
					else {
						changeDescription = allChangeDescriptions[3];
					}
				}
				tableData[i].push(changeDescription);
			}
			else {
				tableData[i].push(allChangeDescriptions[4]);
			}
		}
		
		var testCaseHtml =  key.replace(/\./g, "/") + ".html";
		
		if (typeof(value.htmlLogPath) !== 'undefined') {
			tableData[i].push("<a href='" + rootURL + "/" + value.htmlLogPath + "/" + testCaseHtml + "' target='_blank'>Current Log</a>");
			includeCurrentLogColumn = true;
		}
		else if (typeof(currentHtmlLocation) !== 'undefined') {
			tableData[i].push("<a href='" + currentHtmlLocation + "/" + testCaseHtml + "' target='_blank'>Current Log</a>");
			includeCurrentLogColumn = true;
		}
		
		if (typeof (compareObj) !== 'undefined' && typeof(compareObj.htmlLogPath) !== 'undefined') {
			tableData[i].push("<a href='" + rootURL + "/" + compareObj.htmlLogPath + "/" + testCaseHtml + "' target='_blank'>Previous Log</a>");
			includeCompareLogColumn = true;
		}
		else if (typeof(compareHtmlLocation) !== 'undefined') {
			tableData[i].push("<a href='" + compareHtmlLocation + "/" + testCaseHtml + "' target='_blank'>Previous Log</a>");
			includeCompareLogColumn = true;
		}
		
		i++;
	});
	
	var columns = [
		{ title: "Test Case", className: "aludratest-wrap-column" },
		{ title: "Status", width: "10em", className: "aludratest-status-column aludratest-center" },
		{ title: "Ignored", width: "3em", className: "aludratest-center" }
	];
	if (compare) {
		columns.push({ title: "Change in status", width: "18em", className: "aludratest-center" });
	}
	if (includeCurrentLogColumn) {
		columns.push({ title: "Current Log", width: "10em", className: "aludratest-center" });
	}
	if (includeCompareLogColumn) {
		columns.push({ title: "Compare Log", width: "10em", className: "aludratest-center" });
	}
	
	// add missing columns to data array
	jQuery.each(tableData, function(index, value) {
		var arr = tableData[index];
		while (arr.length < columns.length) {
			arr.push("");
		}
	});	
	
	// register our very own filter method
	jQuery.fn.dataTable.ext.search.push(
		function(settings, data, dataIndex) {
			var legend = d3.select("#chart_pie_current .nv-legend");
			var series = legend.data()[0];
			if (typeof(series) === 'undefined') {
				return true;
			}
			
			var enabledLabels = new Array();
			
			// get all enabled points
			jQuery.each(series, function(index, value) { 
				if (typeof(value.disabled) === 'undefined' || !value.disabled) {
					enabledLabels.push(value.name);
				}
			});
		
			// translate ignoreds
			if (data[2].indexOf("Yes") > -1) {
				var matchingState = data[1] === "PASSED" ? "IGNORED_PASSED" : "IGNORED_FAILED";
				return enabledLabels.indexOf(matchingState) > -1;
			}
			
			return enabledLabels.indexOf(data[1]) > -1;
		}
	);
	
	window['testCases_table'] = jQuery('#testCases_table').DataTable({
			data: tableData,
			columns: columns,
			lengthMenu: [[50, 100, 500, 1000, -1], [ 50, 100, 500, 1000, "All" ]]
	});			
}

