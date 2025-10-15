<%
    ui.decorateWith("appui", "standardEmrPage")
    def configurationIsValid = !locationTagConfig.isLocationSetupRequired()
    def validVisitLocations = locationTagConfig.getValidVisitLocations()
    def validLoginLocations = locationTagConfig.getValidLoginLocations()
    def locationsWithChildren = []
    allLocations.each{l ->
        if (l.childLocations != null && !l.childLocations.isEmpty()) {
            locationsWithChildren.add(l)
        }
    }
%>

<script type="text/javascript" xmlns="http://www.w3.org/1999/html" xmlns="http://www.w3.org/1999/html">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "${ ui.message("pihapps.admin.configureLoginLocations") }", link: "${ ui.pageLink("pihapps", "admin/configureLoginLocations") }" }
    ];
</script>

<style>
    #login-location-instructions {
        font-size: smaller;
    }
    #login-location-instructions p {
        padding: 10px;
    }
    #login-location-form {
        padding: 20px;
    }
    .system-type-option-description {
        display: none;
        padding-left: 20px;
        color: blue;
    }
    .system-type-section {
        display: none;
        padding: 20px;
    }
    .multi-department-login-location-section {
        display: none;
        padding: 20px;
    }
    .login-location-checkboxes input[type=checkbox] {
        float: unset;
    }
</style>

<script type="text/javascript">
    jq(document).ready(function() {

        function setupInitialValues() {
            <% if (configurationIsValid && validVisitLocations.size() == 1) { %>
                <% if (systemType == locationTagConfig.SINGLE_LOCATION) { %>
                    jq("#singleLocationWidget-field").val('${validVisitLocations.get(0).id}');
                <% } else if (systemType == locationTagConfig.MULTI_DEPARTMENT) { %>
                    let visitLocationWidget = jq("#multiDepartmentVisitLocationWidget");
                    jq(visitLocationWidget).val('${validVisitLocations.get(0).id}');
                    jq(visitLocationWidget).change();
                <% } %>
            <% } %>
            jq("input[name='systemType'][value='${systemType}']").click();
        }

        jq("input[name='systemType']").click(function() {
            let value = jq(this).val();
            jq(".system-type-section").hide();
            jq("#system-type-section-" + value).show();
        });

        jq("#multiDepartmentVisitLocationWidget").change(function() {
            let value = jq(this).val();
            jq(".multi-department-login-location-section").hide();
            jq("#multi-department-login-location-section-" + value).show();
        });

        setupInitialValues();
    });
</script>

<h3>${ui.message("pihapps.admin.configureLoginLocations")}</h3>

<div class="note-container">
    <% if (configurationIsValid) { %>
        <div class="note" style="width: 100%;">
            <div class="text">
                <i class="fas fa-fw fa-check-circle" style="vertical-align: middle;"></i>
                ${ ui.message("pihapps.admin.configureLoginLocations.loginLocationsValid") }
            </div>
        </div>
    <% } else { %>
        <div class="note warning" style="width: 100%;">
            <div class="text">
                <i class="fas fa-fw fa-exclamation-circle" style="vertical-align: middle;"></i>
                ${ ui.message("pihapps.admin.configureLoginLocations.loginLocationsInvalid") }
            </div>
        </div>
    <% } %>
</div>

<div id="login-location-instructions">
    <p>
        <b>${ ui.message("pihapps.admin.configureLoginLocations.loginLocations") }:</b>:
        ${ ui.message("pihapps.admin.configureLoginLocations.loginLocationsDescription") }
    </p>
    <p>
        <b>${ ui.message("pihapps.admin.configureLoginLocations.visitLocations") }:</b>:
        ${ ui.message("pihapps.admin.configureLoginLocations.visitLocationsDescription") }
    </p>
</div>

<form id="login-location-form" method="post" action="${ui.pageLink("pihapps", "admin/configureLoginLocations")}">
    <div id="choose-system-type"></div>
    <b>${ ui.message("pihapps.admin.configureLoginLocations.chooseSystemType") }:</b>
    <div class="system-type-option">
        <input name="systemType" value="${locationTagConfig.SINGLE_LOCATION}" type="radio" ${locationTagConfig.SINGLE_LOCATION == systemType ? "checked" : ""}/>
        ${ ui.message("pihapps.admin.configureLoginLocations.singleLocationDescriptionShort") }
        <a href="#" onclick="jq('#single-visit-single-login-description').toggle()">${ ui.message("pihapps.admin.configureLoginLocations.moreInfo") }</a>
        <p id="single-visit-single-login-description" class="system-type-option-description">
            ${ ui.message("pihapps.admin.configureLoginLocations.singleLocationDescriptionLong") }
        </p>
    </div>
    <div class="system-type-option">
        <input name="systemType" value="${locationTagConfig.MULTI_DEPARTMENT}" type="radio" ${locationTagConfig.MULTI_DEPARTMENT == systemType ? "checked" : ""}/>
        ${ ui.message("pihapps.admin.configureLoginLocations.multiDepartmentDescriptionShort") }
        <a href="#" onclick="jq('#single-visit-multiple-login-description').toggle()">${ ui.message("pihapps.admin.configureLoginLocations.moreInfo") }</a>
        <p id="single-visit-multiple-login-description" class="system-type-option-description">
            ${ ui.message("pihapps.admin.configureLoginLocations.multiDepartmentDescriptionLong") }
        </p>
    </div>
    <div class="system-type-option">
        <input name="systemType" value="${locationTagConfig.MULTI_FACILITY}" type="radio" ${locationTagConfig.MULTI_FACILITY == systemType ? "checked" : ""}/>
        ${ ui.message("pihapps.admin.configureLoginLocations.multiFacilityDescriptionShort") }
        <a href="#" onclick="jq('#multiple-visit-multiple-description').toggle()">${ ui.message("pihapps.admin.configureLoginLocations.moreInfo") }</a>
        <p id="multiple-visit-multiple-description" class="system-type-option-description">
            ${ ui.message("pihapps.admin.configureLoginLocations.multiFacilityDescriptionLong") }
        </p>
    </div>

    <div class="system-type-section" id="system-type-section-${locationTagConfig.SINGLE_LOCATION}">
        ${ui.includeFragment("pihapps", "field/location", [
                "id": "singleLocationWidget",
                "formFieldName": "singleLocation",
                "label": ui.message("pihapps.admin.configureLoginLocations.singleLocation")
        ])}
        <input type="submit" />
    </div>

    <div class="system-type-section" id="system-type-section-${locationTagConfig.MULTI_DEPARTMENT}">
        <p>
            <label for="multiDepartmentVisitLocationWidget">${ ui.message("pihapps.admin.configureLoginLocations.multiDepartmentVisitLocation") }</label>
            <select id="multiDepartmentVisitLocationWidget" name="multiDepartmentVisitLocation">
                <option value=""></option>
                <% locationsWithChildren.each{l -> %>
                    <option value="${l.id}">${l.name}</option>
                <% } %>
            </select>
        </p>
        <% locationsWithChildren.each{ visitLoc -> %>
            <div class="multi-department-login-location-section" id="multi-department-login-location-section-${visitLoc.id}">
                <p>${ ui.message("pihapps.admin.configureLoginLocations.multiDepartmentLoginLocations") }</p>
                <% visitLoc.childLocations.each{ loginLoc ->
                    def selected = systemType == locationTagConfig.MULTI_DEPARTMENT && configurationIsValid && validLoginLocations.contains(loginLoc) %>
                    <input type="checkbox" name="multiDepartmentLoginLocations" value="${loginLoc.id}" ${selected ? "checked": ""}>
                    ${loginLoc.name}
                    <br/>
                <% } %>
                <input type="submit" />
            </div>
        <% } %>
    </div>

    <div class="system-type-section" id="system-type-section-${locationTagConfig.MULTI_FACILITY}">
        <p>${ ui.message("pihapps.admin.configureLoginLocations.multiFacilityLocations") }</p>
        <% rootLocations.each{ l -> %>
            ${ui.includeFragment("pihapps", "field/admin/loginLocationCheckboxes", [
                    "location": l,
                    "padding": 10,
                    "visitLocationFormFieldName": "multiFacilityVisitLocations",
                    "loginLocationFormFieldName": "multiFacilityLoginLocations",
                    "initialVisitLocations": validVisitLocations,
                    "initialLoginLocations": validLoginLocations
            ])}
        <% } %>
        <input type="submit" />

    </div>
</form>
