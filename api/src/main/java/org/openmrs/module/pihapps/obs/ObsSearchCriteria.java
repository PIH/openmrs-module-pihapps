package org.openmrs.module.pihapps.obs;

import lombok.Data;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.module.pihapps.SortCriteria;

import java.util.Date;
import java.util.List;

@Data
public class ObsSearchCriteria {
    private Patient patient;
    private List<Concept> concepts;
    private Date onOrBefore;
    private Date onOrAfter;
    private List<SortCriteria> sortCriteria;
    private Integer startIndex;
    private Integer limit;
}
