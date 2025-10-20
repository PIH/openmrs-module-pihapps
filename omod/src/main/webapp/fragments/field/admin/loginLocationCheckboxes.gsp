<%
    def locations = config.locations
    def padding = config.padding
%>
<% if (locations && !locations.isEmpty()) { %>
    <div style="padding-left: ${padding}px" class="login-location-checkboxes">
    <% locations.each{ l -> %>
        <div style="padding-left: 10px;">
            <input type="checkbox" name="${config.loginLocationFormFieldName}" value="${l.id}" /> ${l.name}
        </div>
        ${ui.includeFragment("pihapps", "field/admin/loginLocationCheckboxes", [
            "locations": l.childLocations,
            "padding": padding,
            "loginLocationFormFieldName": config.loginLocationFormFieldName
        ])}
    <% } %>
    </div>
<% } %>
