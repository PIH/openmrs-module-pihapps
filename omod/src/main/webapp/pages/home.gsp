<%
	ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("pihapps", "home.css")
%>
<div id="home-container">
    <% extensions.each { extension ->
        def fragmentParams = extension.extensionParams
        def fragmentProvider = fragmentParams.get("fragmentProvider")
        def fragmentPath = fragmentParams.get("fragmentPath")
    %>
        ${ ui.includeFragment(fragmentProvider, fragmentPath, fragmentParams)}
    <% } %>
</div>
