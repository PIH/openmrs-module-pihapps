<%
    config.require("id")
    config.require("formFieldName")

    def id = config.id
    def formFieldName = config.formFieldName
    def initialValue = config.initialValue // optional, should be of type Patient
    def size = config.size ?: 40  // Optional size of text field
    def placeholder = config.placeholder ?: "coreapps.searchPatientHeading"
%>

<div id="${id}">
    <input type="text"
           id="${ id }-display"
           class="form-control w-${size} autoCompleteText"
           placeholder="${ ui.message(placeholder)}"
           value="${ initialValue ? ui.format( initialValue ) : ""}"
    />
    <input type="hidden" id="${ id }-field" name="${ formFieldName }" class="autoCompleteHidden" value="${ initialValue ? initialValue.uuid : ""}"/>
</div>

<script type="text/javascript">
    jq(document).ready(function() {
        const hiddenField = jq("#${ id }-field");
        const textField = jq("#${ id }-display");
        textField.on("focus", function() {
            textField.autocomplete({
                "source": function (req, add) {
                    const searchString = jq(textField).val();
                    jq.get(openmrsContextPath + "/ws/rest/v1/patient?q="+searchString, function (data) {
                        const suggestions = [];
                        jq.each(data.results, function (i, val) {
                            const item = {};
                            item.value = val.uuid;
                            item.label = val.display;
                            suggestions.push(item);
                        });
                        if (suggestions.length === 0) {
                            hiddenField.val('');
                            textField.css('color', 'red');
                        }
                        add(suggestions);
                    });
                },
                "minLength": 2,
                "focus": function (event, ui) {
                    textField.val(ui.item.label);
                    return false;
                },
                "select": function (event, ui) {
                    hiddenField.val(ui.item.value);
                    textField.val(ui.item.label);
                    textField.css('color', 'black');
                    textField.blur();
                    return false;
                }
            });
        });

        textField.on("change", function () {
            if (textField.val() === "") {
                hiddenField.val("");
            }
        });

        textField.on("blur", function () {
            if (hiddenField.val() === "" || hiddenField.val() === "ERROR") {
                if (textField.val() !== ""){
                    hiddenField.val("ERROR");
                    textField.css('color', 'red');
                }
                else if (textField.val() === "") {
                    hiddenField.val("");
                }
            }
        });
    });
</script>
