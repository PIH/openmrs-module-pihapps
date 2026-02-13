<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("pihapps", "account/account.css")
	ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
	ui.includeJavascript("uicommons", "moment-with-locales.min.js")
	ui.includeJavascript("pihapps", "pagingDataTable.js")
	ui.includeJavascript("pihapps", "conceptUtils.js")
	ui.includeJavascript("pihapps", "dateUtils.js")
%>
<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label")}", link: '${ui.pageLink("coreapps", "systemadministration/systemAdministration")}' },
        { label: "${ ui.message("emr.task.accountManagement.label")}" }
    ];

	moment.locale(window.sessionContext?.locale ?? 'en');

	const pagingDataTable = new PagingDataTable(jq);
	const conceptUtils = new PihAppsConceptUtils(jq);

	const userAccountPage = "${ ui.pageLink("authenticationui", "account/userAccount") }?userId=";
	const editAccountPage = "${ ui.pageLink("pihapps", "account/account") }?personId=";

	const translations = {
		"gender_M": "${ ui.message("Person.gender.male") }",
		"gender_F": "${ ui.message("Person.gender.female") }",
		"true": "${ ui.message("emr.yes") }",
		"false": "${ ui.message("emr.no") }",
		"edit": "${ ui.message("emr.edit") }",
		"enabled": "${ ui.message("pihapps.account.enabled") }",
		"disabled": "${ ui.message("pihapps.account.disabled") }",
	}

	jq(document).ready(function() {

		const getFilterParameterValues = function() {
			return {
				"q": jq("#search-filter").val(),
				"userEnabled": jq("#user-enabled-filter").val(),
			}
		}

		const createLink = function(url, display) {
			return "<a href=\"" + url + "\">" + display + "</a>"
		}

		pagingDataTable.initialize({
			tableSelector: "#accounts-table",
			tableLoadingSelector: "#accounts-loading-section",
			tableInfoSelector: "#accounts-table-info-and-paging",
			endpoint: openmrsContextPath + "/ws/rest/v1/account",
			representation: "full",
			parameters: { ...getFilterParameterValues() },
			columnTransformFunctions: [
				(a) => { return a.person.display },
				(a) => { return a.user ? createLink(userAccountPage + a.user.uuid, a.username) : "" },
				(a) => { return translations['gender_' + a.gender] ?? "" },
				(a) => { return a.providerRole?.display ?? "" },
				(a) => { return a.providerIdentifier ?? "" },
				(a) => { return translations['' + a.userEnabled] },
				(a) => { return createLink(editAccountPage + a.person.uuid, "<button>" + translations['edit'] + "</button>") }
			],
			datatableOptions: {
				oLanguage: {
					sInfo: "${ ui.message("uicommons.dataTable.info") }",
					sZeroRecords: "${ ui.message("uicommons.dataTable.zeroRecords") }",
					sEmptyTable: "${ ui.message("uicommons.dataTable.emptyTable") }",
					sInfoEmpty:  "${ ui.message("uicommons.dataTable.infoEmpty") }",
					sLoadingRecords:  "${ ui.message("uicommons.dataTable.loadingRecords") }",
					sProcessing:  "${ ui.message("uicommons.dataTable.processing") }",
				}
			}
		});

		jq("#filter-section").find(":input").change(function () {
			pagingDataTable.setParameters(getFilterParameterValues())
			pagingDataTable.goToFirstPage();
		});
	});
</script>

<style>
	#filter-section {
		padding-bottom: 20px;
		table-layout: fixed;
	}
	#filter-section input {
		min-width: unset;
	}
	.date .small {
		font-size: unset;
	}
	.col {
		white-space: nowrap;
	}
	.info-and-paging-row {
		padding-top: 5px;
	}
	.paging-navigation {
		padding-left: 10px;
		cursor: pointer;
	}

	.skeleton {
		background-color: #f0f0f0;
		color: transparent;
		height: 2em;
		overflow: hidden;
		display: block;
	}
	.skeleton.image {
		height: 150px;
		width: 100%;
		margin-bottom: 10px;
	}
	.skeleton.title {
		height: 1.5em;
		width: 60%;
		margin-bottom: 10px;
	}
	.skeleton.text {
		height: 1em;
		width: 100%;
		margin-bottom: 8px;
	}
	.skeleton {
		animation: skeleton-loading 0.5s linear infinite alternate;
	}

	@keyframes skeleton-loading {
		0% {
			background-color: #f0f0f0;
		}
		100% {
			background-color: #e0e0e0;
		}
	}
</style>

<h3>${  ui.message("emr.task.accountManagement.label") }</h3>

<div style="display:flex; justify-content: space-between;">
	<div>
		<a href="${ ui.pageLink("pihapps", "account/account") }">
			<button id="create-account-button">${ ui.message("emr.createAccount") }</button>
		</a>
	</div>
</div>

<div style="padding:10px;" class="help-text">
	${ui.message("emr.task.accountManagement.helpText")}
</div>

<div id="filter-section" class="row justify-content-start align-items-end">
	<div class="col">
		<input type="text" size="50" id="search-filter" placeholder="${ ui.message("Provider.search") }" value="" />
	</div>
	<div class="col">
		<label for="user-enabled-filter">${ ui.message("pihapps.account.status") }</label>
		<select id="user-enabled-filter">
			<option value="">${ ui.message("pihapps.all") }</option>
			<option value="true" selected>${ ui.message("pihapps.account.enabled") }</option>
			<option value="false">${ ui.message("pihapps.account.disabled") }</option>
		</select>
	</div>
</div>

<table id="accounts-table">
	<thead>
		<tr>
			<th>${ ui.message("emr.person.name")}</th>
			<th>${ ui.message("emr.user.username") }</th>
			<th>${ ui.message("emr.gender") }</th>
			<th>${ ui.message("emr.account.providerRole.label") }</th>
			<th>${ ui.message("emr.account.providerIdentifier.label") }</th>
			<th>${ ui.message("emr.account.enabled.label") }</th>
			<th>${ ui.message("general.action") }</th>
		</tr>
	</thead>
	<div id="accounts-loading-section" style="display:none;">
		<i class="icon-spinner icon-spin icon-2x"></i>
	</div>
	<tbody></tbody>
</table>

<div id="accounts-table-info-and-paging" style="font-size: .9em">
	<div class="row justify-content-between info-and-paging-row">
		<div class="col paging-info"></div>
		<div class="col text-right">
			<a class="first paging-navigation">${ ui.message("uicommons.dataTable.first") }</a>
			<a class="previous paging-navigation">${ ui.message("uicommons.dataTable.previous") }</a>
			<a class="next paging-navigation">${ ui.message("uicommons.dataTable.next") }</a>
			<a class="last paging-navigation">${ ui.message("uicommons.dataTable.last") }</a>
		</div>
	</div>
	<div class="row justify-content-between info-and-paging-row">
		<div class="col paging-size">${ ui.message("uicommons.dataTable.lengthMenu") }</div>
	</div>
</div>