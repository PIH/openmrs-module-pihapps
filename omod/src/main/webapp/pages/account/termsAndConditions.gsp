<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    jq(function() {
        disableSubmitButton();

        jq("#termsAcceptCheckbox-field").click(function() {
            if (jq(this).is(':checked')) {
                enableSubmitButton();
            } else {
                disableSubmitButton();
            }
        });

        function disableSubmitButton(){
            jq("#save-button").addClass("disabled").attr("disabled", "disabled");
        }

        function enableSubmitButton(){
            jq("#save-button").removeClass("disabled").removeAttr("disabled");
        }
    });
</script>

<style>
    ul, li {
        list-style: disc;
        padding-left: 20px;
        padding-bottom: 5px;
        padding-top: 5px;
    }
</style>

<h3>${ ui.message("pihapps.termsAndConditions") }</h3>

<form method="post" id="termsAndConditionsForm" autocomplete="off">
    <p>
        ${ ui.message("pihapps.termsAndConditionsText") }
    </p>
    <p>
        ${ ui.includeFragment("pihapps", "field/checkbox", [
                label: ui.message("pihapps.termsAndConditionsAccept"),
                id: "termsAcceptCheckbox",
                formFieldName: "termsAndConditionsAccepted",
                value: "true",
                checked: false
        ])}
    </p>
    <div>
        <input type="submit" class="confirm" id="save-button" value="${ ui.message("emr.continue") }" class="disabled" />
    </div>
</form>
