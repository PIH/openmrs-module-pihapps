package org.openmrs.module.pihapps.obs;

import lombok.Data;
import org.openmrs.Obs;

import java.util.List;

@Data
public class ObsSearchResult {
    Long totalCount;
    List<Obs> obs;
}
