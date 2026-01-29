<%
    def useBootstrap = config.containsKey('useBootstrap') ? config.useBootstrap : true;  // use bootstrap unless specifically excluded
%>
<script type="text/javascript">
    var sessionLocationModel = {
        id: () => "${ sessionContext.sessionLocationId }",
        text: () => "${ ui.escapeJs(ui.encodeHtmlContent(loginLocationName)) }"
    }
    jq(document).ready(function () {
        <% if (ui.convertTimezones()) { %>
            var clientCurrentTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
            data = { clientTimezone: clientCurrentTimezone };
            emr.getFragmentActionWithCallback("appui", "header", "setClientTimezone", data , null , null);
        <% } %>
        if (jq("#clientTimezone").length) {
            jq("#clientTimezone").val(Intl.DateTimeFormat().resolvedOptions().timeZone)
        }
    });
</script>
<% if (!useBootstrap) { %>
    <script>
        jq(document).ready(function () {
            jq("#navbarSupportedContent").removeClass("collapse").removeClass("navbar-collapse");
        });
    </script>
    <style>
        .navbar-toggler {
            display: none;
        }
        header:before {
            display: unset;
        }
        header:after {
            display: unset;
        }
        .navbar {
            padding-left: 15px;
            padding-right: 15px;
            display: flex;
            position: relative;
            align-items: center;
        }
        #navbarSupportedContent {
            width: 100%;
        }
        .user-options {
            width: 100%;
        }
        .navbar-nav > li {
            float: unset;
        }
    </style>
<% } %>
<header>
    <nav class="navbar navbar-expand-lg navbar-dark navigation">
        <div class="logo">
            <a href="/${ contextPath }">
                <img src="/${ contextPath }${ configSettings.get("logo-icon-url") }"/>
            </a>
        </div>
        <% if (context.authenticated) { %>
            <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
            <div class="collapse navbar-collapse" id="navbarSupportedContent">
                <ul class="navbar-nav ml-auto user-options">
                    <li class="nav-item identifier">
                        <a href="${ui.pageLink("authenticationui", "account/userAccount")}">
                            <i class="icon-user small"></i>
                            ${ context.authenticatedUser.username ?: context.authenticatedUser.systemId }
                        </a>
                    </li>
                    <% if (sessionContext.sessionLocation) { %>
                        <li class="nav-item">
                            <a href="${ui.pageLink("pihapps", "loginLocation")}">
                                <i class="icon-map-marker small"></i>
                                <span>${ ui.escapeJs(ui.encodeHtmlContent(loginLocationName)) }</span>
                            </a>
                        </li>
                    <% } %>
                    <li class="nav-item logout">
                        <a href="${ ui.actionLink("appui", "header", "logout", ["successUrl": contextPath]) }">
                            ${ui.message("emr.logout")}
                            <i class="icon-signout small"></i>
                        </a>
                    </li>
                </ul>
            </div>
        <% } %>
    </nav>
</header>
