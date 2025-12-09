package org.openmrs.module.pihapps;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SortCriteria {

    public enum Direction {
        ASC, DESC
    }

    private String field;
    private Direction direction;
}
