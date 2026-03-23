<%
    config.require("label")
    config.require("formFieldName")

    // config supports initialValue that can be set on the form
    // config supports defaultValue that is used if the form value is not set
    // config supports withTag, which is a ; delimited list of location tags to include
    // config supports restrictToVisitLocationAndDescendants, which will only show locations that are descendants of the visit location if set true

    def valueField = config.valueField ?: "id" // Enables either id or uuid to be used for the hidden value
    def restrictToVisitLocationAndDescendants = config.restrictToVisitLocationAndDescendants
    def withTag = config.withTag;
    def initialValue = config.initialValue

    if (!initialValue && config.defaultValue) {
        if (config.defaultValue == 'visitLocationForSessionLocation') {
            initialValue = visitLocationForSessionLocation
        }
         else if (config.defaultValue == 'sessionLocation') {
            initialValue = sessionLocation
        }
    }
    if (initialValue instanceof org.openmrs.Location) {
        if (valueField == "uuid") {
            initialValue = ((org.openmrs.Location) initialValue).uuid
        }
        else {
            initialValue = ((org.openmrs.Location) initialValue).id.toString()
        }
    }

    def hasAncestorOrIsAncestor(ancestor, child) {
        if (child == ancestor) {
            return true
        }
        if (child.parentLocation == null) {
            return false
        }
        return hasAncestorOrIsAncestor(ancestor, child.parentLocation)
    }

    def options;
    def tagList = [];
    if (withTag) {
        tagArray = withTag.split(";")
        if(tagArray.size() >0){
            tagArray.each{ t ->
                def tag = t instanceof String ? context.locationService.getLocationTagByName(t) : t
                tagList.add(tag)
            }
        }
        options = context.locationService.getLocationsHavingAnyTag(tagList)
    } else {
        options = context.locationService.allLocations
    }

   if (restrictToVisitLocationAndDescendants) {
        options = options?.findAll { hasAncestorOrIsAncestor(visitLocationForSessionLocation, it) }
   }

    options = options.collect {
        def selected = (valueField == "uuid" ? it.uuid == initialValue : it.id.toString() == initialValue)
        [ label: ui.format(it), value: (valueField == "uuid" ? it.uuid : it.id), selected: selected ]
    }
    options = options.sort { a, b -> a.label <=> b.label }
%>

${ ui.includeFragment("uicommons", "field/dropDown", [ options: options ] << config) }