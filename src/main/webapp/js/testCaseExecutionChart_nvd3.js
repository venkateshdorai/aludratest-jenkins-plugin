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
		result[index].x = index;
	});
	return result;
}

function handleTestCasePointClick(d, testCaseName) {
	// do AJAX request to determine HTML URL for this build
	var buildNumber = projectStats.buildNumbers[d[0].point.x];
	
	navBean.getTestCaseLogPath(testCaseName, buildNumber, function(t) {
		var url = t.responseObject();
		if (url != null && typeof(url) !== "undefined" && url.length > 0) {
			window.open(url, '_blank');
		}
	});	
}

function showTestCaseExecutionChart(testCaseName) {
	// close any previous dialog
	var dlg = window['__tcec_dlg'];
	if (typeof (dlg) !== 'undefined') {
		dlg.hide();
	}
	// create an on-the-fly div for the popup
	jQuery("body").append("<div id='tmpDlgTCEC'><div><div style='font-size: 14px; text-align: center;' id='tmpDlgTCEC_chart_title'></div><svg id='tmpDlgTCEC_chart' style='width: 100%; height: 400px'></svg></div></div>");

	dlg = jQuery("#tmpDlgTCEC").dialog({
		width: 630,
		height: 500,
		close: function() {
			jQuery("#tmpDlgTCEC").remove();
		},
		resize: function(event, ui) {
			window['__tcec_dlg_chart'].update();
		}
	});
	window['__tcec_dlg'] = dlg;
	
	// calculate HTML path for test case
	var testCaseHtmlPath = testCaseName.replace(/\./g, "/") + ".html";
	
	// calculate data
	var data = buildTestCaseExecutionSeriesArray(testCaseName, function(status) {
		if (status == null || typeof (status) === 'undefined') {
			return {
				y: 0,
				color: '#000000'
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
		result.color = fillColor;

		if (typeof (status.ignoredReason) !== 'undefined') {
			result.ignoredReason = status.ignoredReason;
		}
		
		if (typeof (status.htmlLogPath) !== 'undefined') {
			result.htmlLogPath = status.htmlLogPath;
		}

		return result;
	});
	
	// convert to NVD3 format
	data = [{ values: data, strokeWidth: 2.5, color: "#7CB5EC" }];
	
	var pointColors = [];

	var chart = nv.models.lineChart()
		.margin({right: 20, left: 80})
		.useInteractiveGuideline(true)
		.showLegend(false)
		.duration(400)
		.x(function(d) { return d.x; })
		.y(function(d) { return d.y; });
	
	chart.yAxis.axisLabel("Execution Status");
	chart.yAxis.axisLabelDistance(20);
	chart.yAxis.tickFormat(function(d) { 
		return (['No Run', 'Failed', 'Passed'])[d];
	});
	
	chart.xAxis.axisLabel("Build");
	chart.xAxis.tickFormat(function(d) {
		return projectStats.buildLabels[d];
	});
	
	chart.forceY([-0.25, 2.25]);
	chart.forceX([-0.5, projectStats.buildLabels.length - 0.5]);
	
	var headerFormatter = function(d) {
		return projectStats.buildLabels[d];
	};
	
	// the tooltip renderer. We have to replace it completely, unfortunately
    var contentGenerator = function(d) {
        if (d === null) {
            return '';
        }

        var table = d3.select(document.createElement("table"));
        
        var theadEnter = table.selectAll("thead")
            .data([d])
            .enter().append("thead");

        theadEnter.append("tr")
            .append("td")
            .attr("colspan", 2)
            .append("strong")
            .classed("x-value", true)
            .html(headerFormatter(d.value));

        var tbodyEnter = table.selectAll("tbody")
            .data([d])
            .enter().append("tbody");

        var trowEnter = tbodyEnter.selectAll("tr")
            .data(function(p) { return p.series})
            .enter()
            .append("tr")
            .classed("highlight", function(p) { return p.highlight});

        trowEnter.append("td")
        	.html(function(p, i) { 
        		var point = p.data;
        		
        		if (typeof(point.status) === 'undefined') {
        			return "<i>No Run</i>";
        		}
        		
        		var html = "<strong>" + point.status + "</strong>";
        		if (point.ignored == 1) {
        			html += " <i>(ignored)</i>";
        		}
        		
        		if (typeof(point.ignoredReason) !== 'undefined') {
        			html += "<br /><br />Reason for ignore:<br /><i>" + point.ignoredReason + "</i>";
        		}
        		
        		return html; 
        	});

        var html = table.node().outerHTML;
        if (d.footer !== undefined)
            html += "<div class='footer'>" + d.footer + "</div>";
        return html;
    };
	
    chart.interactiveLayer.tooltip.contentGenerator(contentGenerator);
    
    // hook into point click
    chart.lines.dispatch.on("elementClick", function(d) { handleTestCasePointClick(d, testCaseName); });
    
	// hack for having points being visible and with right color set
	var activatePoints = function(chart) {
		var pointColor = function(d, i) {
			return d[0].color;
		} 
		d3.selectAll('#tmpDlgTCEC_chart .nv-lineChart .nv-point').style("stroke-width", "7px")
			.style("fill-opacity", ".95").style("stroke-opacity", ".95")
			.style("stroke", pointColor).style("fill", pointColor);
	};
	
	nv.addGraph(function() {
		d3.select('#tmpDlgTCEC_chart')
	      .datum(data)
	      .call(chart);
		
		return chart;
	}, activatePoints);
	
	
	window['__tcec_dlg_chart'] = chart;
	
	var shortTestCaseName = testCaseName;
	
	// TODO best would be to have it adjusted to window width
	var maxLength = 70;
	while (shortTestCaseName.length > maxLength) {
		if (shortTestCaseName.indexOf(".") > -1 && shortTestCaseName.length - shortTestCaseName.indexOf(".") >= 10) {
			shortTestCaseName = shortTestCaseName.substring(shortTestCaseName.indexOf(".") + 1);
		}
		else {
			shortTestCaseName = "..." + shortTestCaseName.substring(shortTestCaseName.length - maxLength + 3);
		}
	}
	jQuery("#tmpDlgTCEC_chart_title").text(shortTestCaseName);
}
