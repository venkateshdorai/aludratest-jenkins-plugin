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
function buildTestCaseExecutionSeriesArray(testCaseName, executionStatusCallback) {
	var result = new Array();
	jQuery.each(projectStats.testCaseStatistics[testCaseName].results, function(index, value) {
		result[index] = executionStatusCallback(value);
	});
	return result;
}

function showTestCaseExecutionChart(testCaseName) {
	// close any previous dialog
	var dlg = window['__tcec_dlg'];
	if (typeof (dlg) !== 'undefined') {
		dlg.hide();
	}
	// create an on-the-fly div for the popup
	jQuery("body").append("<div id='tmpDlgTCEC'><div id='tmpDlgTCEC_chart' style='width: 100%'></div></div>");

	dlg = jQuery("#tmpDlgTCEC").dialog({
		width: 630,
		height: 500,
		close: function() {
			jQuery("#tmpDlgTCEC").remove();
		},
		resize: function(event, ui) {
			window['__tcec_dlg_chart'].setSize(jQuery('#tmpDlgTCEC_chart').width(), jQuery('#tmpDlgTCEC_chart').height());
		}
	});
	window['__tcec_dlg'] = dlg;
	
	// calculate HTML path for test case
	var testCaseHtmlPath = testCaseName.replace(/\./g, "/") + ".html";

	window['__tcec_dlg_chart'] = new Highcharts.Chart('tmpDlgTCEC_chart', {
		title: {
			text: testCaseName
		},
		xAxis: {
			categories: projectStats.buildLabels,
			title: {
				text: 'Build'
			}
		},
		yAxis: {
			categories: [ 'No Run', 'Failed', 'Passed' ],
			title: {
				text: 'Execution Status'
			},
			allowDecimals: false,
			min: 0,
			max: 2
		},
		plotOptions: {
			series: {
				allowPointSelect: false,
				cursor: 'pointer',
				marker: {
					lineColor: 'none',
					fillColor: '#ff0000',
					states: {
						hover: {
							lineColor: 'none',
							fillColor: '#ff0000'
						}
					}
				}
			}
		},
		legend: {
			enabled: false
		},
		tooltip: {
			formatter: function() {
				var result = '<span style="color:' + this.point.marker.fillColor + '">\u25CF</span> ' + this.x + ': <b>'
						+ this.point.status + '</b><br/>';
				if (this.point.ignored) {
					result += "<i>(Ignored)</i>";
					if (typeof (this.point.ignoredReason) !== 'undefined') {
						result += "<br/>Reason for ignore: " + this.point.ignoredReason;
					}
					result += "<br/>"
				}
				return result;
			}
		},

		series: [ {
			point: {
				events: {
					click: function() {
						// do AJAX request to determine HTML URL for this build
						var buildNumber = projectStats.buildNumbers[this.x];
						
						navBean.getTestCaseLogPath(testCaseName, buildNumber, function(t) {
							var url = t.responseObject();
							if (url != null && typeof(url) !== "undefined" && url.length > 0) {
								window.open(url, '_blank');
							}
						});
					}
				}
			},
			data: buildTestCaseExecutionSeriesArray(testCaseName, function(status) {
				if (status == null || typeof (status) === 'undefined') {
					return {
						y: 0,
						marker: {
							fillColor: '#000000',
							states: {
								hover: {
									fillColor: '#000000'
								}
							}
						},
						status: 'No Run'
					};
				}
				var result = {
					y: 1
				};
				var fillColor = statusColors[status.status];
				if (status.ignored == 1) {
					fillColor = statusColors['IGNORED'];
					result.ignored = true;
				}
				result.status = status.status;

				if (status.success == 1) {
					result.y++;
				}
				result.marker = {
					fillColor: fillColor,
					states: {
						hover: {
							fillColor: fillColor
						}
					}
				}

				if (typeof (status.ignoredReason) !== 'undefined') {
					result.ignoredReason = status.ignoredReason;
				}
				
				if (typeof (status.htmlLogPath) !== 'undefined') {
					result.htmlLogPath = status.htmlLogPath;
				}

				return result;
			})
		} ]
	});

}
