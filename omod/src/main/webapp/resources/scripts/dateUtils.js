/**
 * Adds javascript functions useful for working with dates
 */
class PihAppsDateUtils {

    constructor(moment, dateFormat, dateTimeFormat) {
        this.moment = moment;
        this.dateFormat = dateFormat ?? "DD-MMM-YYYY";
        this.dateTimeFormat = dateTimeFormat ?? "DD-MMM-YYYY HH:mm";
    }

    hasTime(dateStr) {
        const m = this.moment(dateStr);
        return (m.hour() !== 0 || m.minute() !== 0 || m.second() !== 0 || m.millisecond() !== 0);
    }

    formatDateWithTimeIfPresent(dateStr) {
        return dateStr ? this.moment(dateStr).format(this.hasTime(dateStr) ? this.dateTimeFormat : this.dateFormat) : "";
    }

    roundDownToNearestMinuteInterval(date, minuteInterval) {
        const d = new Date(date);
        d.setSeconds(0);
        d.setMilliseconds(0);
        let minutes = d.getMinutes();
        while (minutes % minuteInterval !== 0) {
            minutes--;
        }
        d.setMinutes(minutes)
        return d;
    }

    /**
     * options;
     *   - id
     *   - name
     *   - label
     *   - locale
     *   - initialValue
     *   https://github.com/diasks2/bootstrap-datetimepicker-1
     */
    createDatePickerWidget(jq, options) {
        const widget = jq("<span>").attr("id", options.id).addClass("date-widget");
        const label = jq("<label>").attr("for", options.id + "-display").html(options.label ?? "");
        const dateWrapper = jq("<span>").attr("id", options.id + "-wrapper").addClass("date");
        const dateDisplayInput = jq("<input>").attr("type", "text").attr("id", options.id + "-display");
        const dateCalendarIcon = jq("<span>").addClass("add-on").append(jq("<i>").addClass("icon-calendar small"));
        const dateRemoveIcon = jq("<i>").addClass("icon-remove small");
        dateWrapper.append(dateDisplayInput).append(dateCalendarIcon).append(dateRemoveIcon);
        const hiddenInput = jq("<input>").attr("type", "hidden").attr("id", options.id + "-field").attr("name", options.name ?? "");
        const fieldErrorSection = jq("<span>").addClass("field-error").css("display: none");
        widget.append(label).append(dateWrapper).append(hiddenInput).append(fieldErrorSection);

        const initialValue = options.initialValue ? moment(options.initialValue) : null;
        dateDisplayInput.val(initialValue ? initialValue.format(this.dateTimeFormat) : "");
        hiddenInput.val(initialValue ? initialValue.format("YYYY-MM-DD HH:mm:ss") : "");

        widget.find(".date").datetimepicker({
            autoclose: true,
            pickerPosition: "bottom-left",
            todayHighlight: false,
            minuteStep: 5,
            format: "dd M yyyy hh:ii",
            language: options.locale ?? "en",
            linkField: options.id + "-field",
            linkFormat: "yyyy-mm-dd hh:ii:ss",
        });

        const inputField = widget.find("input");
        widget.find(".icon-remove").on("click", () => {
            if (inputField.val() !== "") {
                inputField.val("").change();
            }
        });
        return widget;
    }
}
