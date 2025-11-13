<%
    ui.decorateWith("appui", "standardEmrPage")
    def visitAndLoginLocations = locationTagConfig.getValidVisitAndLoginLocations()
    def visitLocations = visitAndLoginLocations.keySet()
%>

<script type="text/javascript" xmlns="http://www.w3.org/1999/html" xmlns="http://www.w3.org/1999/html">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("pihapps.login.chooseLocation.title") }" }
    ];
</script>

<style>
    #visit-location-section {
        padding-bottom: 20px;
    }
    #visit-location-section label {
        font-weight: bold;
    }
    .login-location-section {
        display: none;
    }
    .login-location-section label {
        font-weight: bold;
    }
    #login-location-select {
        margin-top: 10px;
    }
    form ul.visit-location-select {
        margin-top: 10px;
    }
    #overlay {
        position: fixed;
        display: none;
        width: 100%;
        height: 100%;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: rgba(0,0,0,0.5);
        z-index: 2;
    }
    #overlay-content{
        position: absolute;
        top: 50%;
        left: 50%;
        font-size: 50px;
        color: white;
        transform: translate(-50%,-50%);
        -ms-transform: translate(-50%,-50%);
    }
    #cancel-button-section {
        padding-top: 10px;
    }
</style>

<script type="text/javascript">
    jq(document).ready(function() {

        function showLoginLocationSection(visitLocationId) {
            jq("#login-location-section").hide();
            jq(".login-location-item").hide();
            let loginLocationElements = jq(".login-location-item-"+visitLocationId);
            if (loginLocationElements.length === 1) {
                jq(loginLocationElements[0]).click();
            }
            else {
                jq(loginLocationElements).show();
                jq("#login-location-section").show();
            }
        }

        jq(".visit-location-select .location-list-item").click(function() {
            const id = jq(this).attr('value');
            jq(".visit-location-select .location-list-item").removeClass('selected');
            jq(this).addClass('selected');
            showLoginLocationSection(id);
        });

        jq(".login-location-select .location-list-item").click(function() {
            const id = jq(this).attr('value');
            jq(".login-location-select .location-list-item").removeClass('selected').addClass('location-disabled');
            jq(this).addClass('selected');
            jq("#session-location-input").val(id);
            <% if (!locationTagConfig.isLocationSetupRequired()) { %>
                jq("#overlay").show();
                jq("#login-location-form").submit();
            <% } %>
        });

        <% if (visitLocations.size() == 1) { %>
            showLoginLocationSection('${visitLocations.iterator().next().id}');
            <% if (currentLoginLocation) { %>
                jq("#login-location-select-item-${currentLoginLocation.id}").addClass('selected');
            <% } %>
        <% } else if (currentVisitLocation && visitAndLoginLocations.get(currentVisitLocation).size() > 1) { %>
            jq("#visit-location-select-item-${currentVisitLocation.id}").click();
        <% } %>
    });
</script>

<% if (locationTagConfig.isLocationSetupRequired()) { %>

    <style>
    .setup-location-tag-link {
        color: blue;
        text-decoration: underline;
    }
    </style>
    <div class="note-container">
        <div class="note warning" style="width: 100%;">
            <div class="text">
                <i class="fas fa-fw fa-exclamation-circle" style="vertical-align: middle;"></i>
                <% if (sessionContext.currentUser.hasPrivilege("App: coreapps.systemAdministration")) { %>
                    <a class="setup-location-tag-link" href="${ ui.pageLink("pihapps", "admin/configureLoginLocations") }">
                        ${ ui.message("pihapps.login.warning.invalidLoginLocations") }
                    </a>
                <% } else { %>
                    ${ ui.message("pihapps.login.warning.invalidLoginLocations") }
                <% } %>
            </div>
        </div>
    </div>

<% } else { %>

    <form id="login-location-form" method="post">
        <!-- only show visit location selector if there are multiple locations to choose from -->
        <% if (visitLocations.size() > 1) { %>
            <div class="clear" id="visit-location-section">
                <label>
                    ${ ui.message("pihapps.login.chooseVisitLocation.title") }:
                </label>
                <ul class="select visit-location-select">
                    <% visitLocations.each { visitLocation -> %>
                        <li id="visit-location-select-item-${visitLocation.id}" class="location-list-item" value="${visitLocation.id}">${ui.format(visitLocation)}</li>
                    <% } %>
                </ul>
            </div>
        <% } %>

        <% if (visitLocations.size() == 1) { %>
            <h3>${ ui.format(visitLocations.iterator().next()) }</h3>
        <% } %>
        <div class="clear login-location-section" id="login-location-section">
            <label>
                ${ ui.message("pihapps.login.chooseLoginLocation.title") }:
            </label>
            <ul id="login-location-select" class="select login-location-select">
                <% visitLocations.each { visitLocation ->
                    def loginLocations = visitAndLoginLocations.get(visitLocation)
                    loginLocations.each { loginLocation -> %>
                        <li id="login-location-select-item-${loginLocation.id}" class="location-list-item login-location-item login-location-item-${visitLocation.id}" value="${loginLocation.id}">${ui.format(loginLocation)}</li>
                    <% } %>
                <% } %>
            </ul>
        </div>

        <div id="overlay">
            <div id="overlay-content">
                <i class="icon-spinner icon-spin icon-2x"></i>
            </div>
        </div>

        <input type="hidden" name="returnUrl" value="${ returnUrl }"/>
        <input id="session-location-input" type="hidden" name="sessionLocation" />
    </form>

    <% if (currentLoginLocation) { %>
        <div id="cancel-button-section">
            <input type="button" class="cancel" value="${ ui.message("coreapps.cancel") }" onclick="window.history.back()" />
        </div>
    <% } %>

<% } %>