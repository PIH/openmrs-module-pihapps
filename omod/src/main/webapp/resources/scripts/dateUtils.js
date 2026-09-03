/**
 * Adds javascript functions useful for working with dates
 */
class PihAppsDateUtils {

    constructor(moment, dateFormat, dateTimeFormat) {
        this.moment = moment;
        this.dateFormat = dateFormat ?? "DD-MMM-YYYY";
        this.dateTimeFormat = dateTimeFormat ?? "DD-MMM-YYYY HH:mm";
    }

    // Dates from the server are always shown/interpreted in the offset the server rendered them in
    // (parseZone), rather than converted to the browser's local timezone (plain moment(dateStr)) -
    // the two can differ, and the app should reflect the server's/hospital's time, not the browser's.

    hasTime(dateStr) {
        const m = this.moment.parseZone(dateStr);
        return (m.hour() !== 0 || m.minute() !== 0 || m.second() !== 0 || m.millisecond() !== 0);
    }

    formatDateWithTimeIfPresent(dateStr) {
        return dateStr ? this.moment.parseZone(dateStr).format(this.hasTime(dateStr) ? this.dateTimeFormat : this.dateFormat) : "";
    }

    formatAsDateWithoutTime(dateStr) {
        return dateStr ? this.moment.parseZone(dateStr).format(this.dateFormat) : "";
    }

    getDate(dateStr) {
        return dateStr ? this.moment.parseZone(dateStr).toDate() : null;
    }

    formatAsIsoDate(dateStr) {
        return dateStr ? this.moment.parseZone(dateStr).format("YYYY-MM-DD") : "";
    }

    formatAsIsoDateTime(dateStr) {
        return dateStr ? this.moment.parseZone(dateStr).format("YYYY-MM-DD HH:mm:ss") : "";
    }

    // Returns a moment (in the offset of the passed-in date string) rounded down to the nearest
    // minuteInterval, so callers building a default/bound from it keep the server's offset rather
    // than the browser's.
    roundDownToNearestMinuteInterval(date, minuteInterval) {
        const m = this.moment.parseZone(date).seconds(0).milliseconds(0);
        return m.minutes(m.minutes() - (m.minutes() % minuteInterval));
    }
}
