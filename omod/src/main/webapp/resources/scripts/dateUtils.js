/**
 * Adds javascript functions useful for working with dates
 */
class PihAppsDateUtils {

    constructor(moment) {
        this.moment = moment;
    }

    hasTime(dateStr) {
        const m = this.moment(dateStr);
        return (m.hour() !== 0 || m.minute() !== 0 || m.second() !== 0 || m.millisecond() !== 0);
    }

    formatDateWithTimeIfPresent(dateStr, dateFormat, dateTimeFormat) {
        return this.moment(dateStr).format(this.hasTime(dateStr) ? dateTimeFormat : dateFormat);
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
}
