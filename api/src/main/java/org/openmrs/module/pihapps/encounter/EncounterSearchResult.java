package org.openmrs.module.pihapps.encounter;

import lombok.Data;
import org.openmrs.Encounter;

import java.util.List;

@Data
public class EncounterSearchResult {
    Long totalCount;
    List<Encounter> encounters;
}
