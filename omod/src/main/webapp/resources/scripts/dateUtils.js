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
     * The rest module expects dates in yyyy-MM-dd, and datetimes in yyyy-MM-dd HH:mm formats
     * However, the rest module produces dates in "yyyy-MM-ddTHH:m:ss" formats.  So this converts to the expected format if needed.
     */
    normalizeDateStrForRest(inputStr, includeTime) {
        const dateTimeRegex = new RegExp("^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}-[0-9]{2}:[0-9]{2})$");
        if (inputStr && dateTimeRegex.test(inputStr)) {
            if (includeTime) {
                return inputStr.substring(0, 16).replace("T", " ");
            } else {
                return inputStr.substring(0, 10)
            }
        }
        return inputStr;
    }

}
