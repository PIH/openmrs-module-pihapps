package org.openmrs.module.pihapps;

import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.api.ConceptNameType;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class PihAppsUtils {

    private final PihAppsConfig pihAppsConfig;

    private final MessageSourceService messageSourceService;

    @Autowired
    public PihAppsUtils(PihAppsConfig pihAppsConfig, MessageSourceService messageSourceService) {
        this.pihAppsConfig = pihAppsConfig;
        this.messageSourceService = messageSourceService;
    }

    public String formatLabTest(Concept c) {
        if ("shortest".equals(pihAppsConfig.getLabOrderConfig().getConceptDisplayFormat())) {
            return getBestShortName(c);
        }
        return c.getDisplayString();
    }

    /**
     * @return the best short name for a concept
     * Taken from orderentryowa - helpers.getConceptShortName
     */
    public String getBestShortName(Concept c) {

        // If a specific short name has been added to message properties for this concept, prioritize that
        String messageCode = "ui.i18n.Concept.shortName." + c.getUuid();
        String translatedMessageCode = messageSourceService.getMessage(messageCode);
        if (!translatedMessageCode.equals(messageCode)) {
            return translatedMessageCode;
        }

        ConceptName preferredShortLocale = null;
        ConceptName shortLocale = null;
        ConceptName preferredLocale = null;
        ConceptName preferredShortEnglish = null;
        ConceptName shortEnglish = null;
        if (c == null || c.getNames() == null || c.getNames().isEmpty()) {
            return "";
        }
        // Get the locale for the current locale, language only
        Locale locale = Context.getLocale();
        String language = locale.getLanguage();
        for (ConceptName cn : c.getNames()) {
            boolean isShort = cn.getConceptNameType() == ConceptNameType.SHORT;
            boolean isPreferred = cn.isPreferred();
            boolean isLocale = cn.getLocale().equals(locale) || cn.getLocale().getLanguage().equals(language);
            boolean isEnglish = cn.getLocale().getLanguage().equals("en");
            if (isPreferred && isShort && isLocale) {
                preferredShortLocale = cn;
            }
            else if (isShort && isLocale) {
                shortLocale = cn;
            }
            else if (isPreferred && isLocale) {
                preferredLocale = cn;
            }
            else if (isPreferred && isShort && isEnglish) {
                preferredShortEnglish = cn;
            }
            else if (isShort && isEnglish) {
                shortEnglish = cn;
            }
        }
        if (preferredShortLocale != null) {
            return preferredShortLocale.getName();
        }
        if (shortLocale != null) {
            return shortLocale.getName();
        }
        if (preferredLocale != null) {
            return preferredLocale.getName();
        }
        if (preferredShortEnglish != null) {
            return preferredShortEnglish.getName();
        }
        if (shortEnglish != null) {
            return shortEnglish.getName();
        }
        return c.getDisplayString();
    }

    /**
     * @param root
     * @return a set of Concepts that are recursive set members of root
     */
    /**
     * The last moment of a date's day if that date carries no time of day, and the date itself
     * otherwise. Modelled on the reporting module's {@code DateUtil.getEndOfDayIfTimeExcluded}.
     *
     * <p>This is for the upper end of an inclusive range. Someone who names a day means the whole
     * of it, so a bound of `2026-09-30` has to reach 23:59:59.999 or everything recorded after
     * midnight on the 30th falls outside a range that plainly includes the 30th. A lower bound
     * needs no such adjustment: midnight is already the first moment of its day.
     *
     * <p>A time of exactly midnight is read as no time of day, since a Date cannot say whether the
     * caller wrote `2026-09-30` or `2026-09-30T00:00:00`. A caller that means that first instant
     * and nothing more should bound the range a moment earlier.
     *
     * @param date the upper bound as given, or null for no bound
     * @return the bound to search on, or null if none was given
     */
    public static Date getEndOfDayIfTimeExcluded(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        if (calendar.get(Calendar.HOUR_OF_DAY) != 0 || calendar.get(Calendar.MINUTE) != 0
                || calendar.get(Calendar.SECOND) != 0 || calendar.get(Calendar.MILLISECOND) != 0) {
            return date;
        }
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    public static Set<Concept> getConceptHierarchy(Concept root) {
        Set<Concept> result = new HashSet<>();
        Set<Integer> visited = new HashSet<>();
        Deque<Concept> queue = new ArrayDeque<>();
        visited.add(root.getConceptId());
        queue.add(root);
        while (!queue.isEmpty()) {
            Concept concept = queue.poll();
            result.add(concept);
            for (Concept member : concept.getSetMembers()) {
                if (visited.add(member.getConceptId())) {
                    queue.add(member);
                }
            }
        }
        return result;
    }
}
