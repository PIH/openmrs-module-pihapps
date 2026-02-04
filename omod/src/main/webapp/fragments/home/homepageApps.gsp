<div id="apps">
    <% extensions.each { extension -> %>
        <a id="${ extension.id.replace(".", "-") }-app" href="/${ contextPath }/${ extension.url }" class="button app big">
            <% if (extension.icon) { %>
                <i class="${ extension.icon }"></i>
            <% } %>
            ${ ui.message(extension.label) }
        </a>
    <% } %>
</div>