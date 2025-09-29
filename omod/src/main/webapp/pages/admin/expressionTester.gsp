<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("pihapps", "lib/codemirror/lib/codemirror.css")
    ui.includeCss("pihapps", "lib/codemirror/addon/hint/show-hint.css")
    ui.includeCss("pihapps", "lib/codemirror/theme/monokai.css")
    ui.includeJavascript("pihapps", "lib/codemirror/lib/codemirror.js")
    ui.includeJavascript("pihapps", "lib/codemirror/mode/javascript/javascript.js")
    ui.includeJavascript("pihapps", "lib/codemirror/addon/edit/matchbrackets.js")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "Extension Expression Tester", link: "${ ui.pageLink("pihapps", "admin/expressionTester") }" }
    ];
    jq(document).ready(function() {
        window.statusExpressionEditor = CodeMirror.fromTextArea(document.getElementById('require-expression'), {
            mode: 'text/javascript',
            indentWithTabs: true,
            smartIndent: true,
            lineNumbers: true,
            viewportMargin: Infinity,
            matchBrackets: true,
            autofocus: true,
            theme: "monokai",
            extraKeys: {"Ctrl-Space": "autocomplete"}
        });
        window.statusExpressionEditor.setSize(null, 100);
        <% if (!extension && !expressionToTest) { %>
            jq("#extension-section").hide();
        <% } %>
    });
</script>

<div>
    <form>
        <br/>
        <label for="patient-selector">Patient: </label>
        <input id="patient-selector" name="patientRef" type="text" size="20" value="${patientRef}" placeholder="Patient ID or UUID" autocomplete="off"/>
        <br/>
        <label for="patient-selector">Visit: </label>
        <input id="patient-selector" name="visitRef" type="text" size="20" value="${visitRef}" placeholder="Optional Visit ID or UUID" autocomplete="off"/>
        <br/>
        <div><a id="context-toggle" onclick="jq('#context-display').toggle();" style="cursor: pointer;">
            View/Hide Require Expression Context
        </a></div>
        <br/>
        <pre id="context-display" style="font-size: 8px; display:none;">
            ${requireExpressionContext}
        </pre>
        <div style="display: table-cell">
            <label for="extension-point-selector">Extension Point: </label>
            <select id="extension-point-selector" name="extensionPoint">
                <option value=""></option>
                <% extensionPoints.each { ep -> %>
                    <option value="${ep}"<%= ep.equals(extensionPoint) ? "selected" : "" %>>${ep}</option>
                <% } %>
            </select>
        </div>
        <div style="display: table-cell; padding-left: 5px;">
            <label for="submit-button">&nbsp;</label>
            <input id="submit-button" type="submit" value="Get Extensions"/>
        </div>
        <br/>
    </form>
    <hr/>

    <% if (extensionPoint) {%>
        <table>
            <tr>
                <th>Extension</th><th>Require Expression</th><th>Passed</th><th>Errors</th>
            </tr>
            <% evaluationResults.each { e -> %>
                <tr>
                        <td>
                            <a href="${ui.pageLink("pihapps", "admin/expressionTester", [
                                    "patientRef": patientRef,
                                    "visitRef": visitRef,
                                    "extensionPoint": extensionPoint,
                                    "extension": e.extension.id
                            ])}">
                                ${e.extension.id}
                            </a>
                        </td>
                        <td>${e.extension.require == null ? "" : e.extension.require}</td>
                        <td>${e.passed}</td>
                        <td>${e.exception ?: ""}</td>
                </tr>
            <% } %>
        </table>
    <% } %>

    <div id="extension-section">

        <br/>
        <form method="post">
            <textarea id="require-expression" name="expressionToTest">${expressionToTest == null ? "" : expressionToTest}</textarea>
            <br/>
            <input type="submit" value="Test"/>
        </form>
        <hr/>
        <div>
            <b>Result: ${expressionToTestResult}${expressionToTestException ? "; ERROR: " + expressionToTestException : ""}</b>
        </div>
    </div>
</div>