/**
 * Wraps a jQuery dataTable and customizes it to allow for server-side paging of results
 * options: {
 *     tableSelector:  The page is expected to have a table in place, with columns defined.  This is the selector for that table.
 *     tableInfoSelector:  The page is expected to have a div in place with appropriately classed elements for displaying result and paging info
 *     datatableOptions:   If any overrides or additional configuration should be passed to the wrapped dataTable, define them here
 *     endpoint:  The endpointUrl to hit to retrieve results
 *     representation:  The endpoint representation to use
 *     parameters: Any initial parameters to configure for the endpoint
 *     columnTransformFunctions:  An array of functions that can transform one result returned from the endpoint to the column value at the appropriate index
 *     pagingSizes: An array of page sizes to support
 *     defaultPageSize: The default page size
 * }
 */
class PagingDataTable {

    /**
     * @param jq the jQuery instance
     * @param options these are the configuration options
     */
    constructor(jq, options) {
        this.jq = jq;

        // Configuration
        this.tableSelector = options.tableSelector;
        this.tableInfoSelector = options.tableInfoSelector;
        this.datatableOptions = { ...this.getDefaultDataTableOptions(), ...options.datatableOptions || {} };
        this.endpoint = options.endpoint;
        this.representation = options.representation;
        this.columnTransformFunctions = options.columnTransformFunctions;
        this.pagingSizes = options.pagingSizes || [10, 15, 20, 25, 50, 100];
        this.defaultPageSize = options.defaultPageSize || 10;
        this.parameters = options.parameters || {};

        // Instance data
        this.pagedTable = null;
        this.pageNumber = 0;
        this.pageSize = 10;
        this.totalCount = 0;
    }

    initialize() {
        this.initializePagingElements();
        this.recreateTable();
        this.goToFirstPage();
    }

    getTableElement() {
        return jq(this.tableSelector);
    }

    getTableInfoElement() {
        return jq(this.tableInfoSelector);
    }

    getPagedTable() {
        return this.pagedTable;
    }

    getPageNumber() {
        return this.pageNumber;
    }

    getPageSize() {
        return this.pageSize;
    }

    getDefaultDataTableOptions() {
        return {
            bFilter: false,
            bJQueryUI: true,
            iDisplayLength: this.pageSize,
            bSort: false,
            bAutoWidth: false,
            sDom: 'ft<\"fg-toolbar ui-toolbar ui-corner-bl ui-corner-br ui-helper-clearfix\">',
        }
    }

    recreateTable() {
        if (this.pagedTable) {
            this.pagedTable.fnDestroy();
        }
        this.pagedTable = this.getTableElement().dataTable(this.datatableOptions);
    }

    setParameters(parameters) {
        this.parameters = parameters;
    }

    updateTable() {
        const pagingParameters = {
            "totalCount": true,
            "startIndex": this.getPageNumber() * this.getPageSize(),
            "limit": this.pageSize
        }
        const representationParameters = this.representation ? { "v": this.representation } : {};
        const requestParameters = { ...this.parameters, ...pagingParameters, ...representationParameters};
        jq.get(this.endpoint, requestParameters, (data) => {
            if (!data || !data.results || data.results.length === 0) {
                this.getPagedTable().fnClearTable();
                this.setTotalCount(0);
                this.pageNumber = 0;
                this.getTableInfoElement().hide();
                return;
            }
            let tableRows = [];
            data.results.forEach((result) => {
                let tableRow = [];
                this.columnTransformFunctions.forEach(transformFunction => {
                    tableRow.push(transformFunction(result));
                });
                tableRows.push(tableRow);
            });
            this.pagedTable.fnClearTable();
            this.pagedTable.fnAddData(tableRows);
            this.setTotalCount(data.totalCount);

            const infoTemplate = this.datatableOptions.oLanguage.sInfo || 'Showing _START_ to _END_ of _TOTAL_ entries';
            const infoMessage = infoTemplate.replace('_START_', this.getFirstNumberForPage()).replace('_END_', this.getLastNumberForPage()).replace('_TOTAL_', this.totalCount);
            this.getTableInfoElement().find(".paging-info").html(infoMessage);

            if (this.hasPreviousRecords()) {
                this.getTableInfoElement().find(".first").removeClass("ui-state-disabled");
                this.getTableInfoElement().find(".previous").removeClass("ui-state-disabled");
            }
            else {
                this.getTableInfoElement().find(".first").addClass("ui-state-disabled");
                this.getTableInfoElement().find(".previous").addClass("ui-state-disabled");
            }
            if (this.hasNextRecords()) {
                this.getTableInfoElement().find(".next").removeClass("ui-state-disabled");
                this.getTableInfoElement().find(".last").removeClass("ui-state-disabled");
            }
            else {
                this.getTableInfoElement().find(".next").addClass("ui-state-disabled");
                this.getTableInfoElement().find(".last").addClass("ui-state-disabled");
            }

            this.pagedTable.fnDraw();
            this.getTableInfoElement().show();
        });
    }

    initializePagingElements() {
        this.getTableInfoElement().find(".first").click(() => this.goToFirstPage());
        this.getTableInfoElement().find(".previous").click(() => this.goToPreviousPage());
        this.getTableInfoElement().find(".next").click(() => this.goToNextPage());
        this.getTableInfoElement().find(".last").click(() => this.goToLastPage());

        const pagingSizeElement = this.getTableInfoElement().find(".paging-size");
        pagingSizeElement.html(pagingSizeElement.html().replace('_MENU_', '<select class="page-size-selector"></select>'));
        const pageSizeSelector = jq(".page-size-selector");
        this.pagingSizes.forEach(size => {
            pageSizeSelector.append('<option value="' + size + '">' + size + '</option>');
        });
        pageSizeSelector.val(this.defaultPageSize);
        pageSizeSelector.on("change", () => {
            this.setPageSize(this.value);
            this.recreateTable();
            this.goToFirstPage();
        });
    }

    setTotalCount(totalCount) {
        this.totalCount = totalCount;
    }

    setPageSize(pageSize) {
        this.pageSize = pageSize;
    }

    hasPreviousRecords() {
        return this.pageNumber > 0;
    }

    hasNextRecords() {
        return this.getLastNumberForPage() < this.totalCount;
    }

    goToNextPage() {
        if (this.hasNextRecords()) {
            this.pageNumber++;
        }
        this.updateTable();
    }

    goToPreviousPage() {
        if (this.hasPreviousRecords()) {
            this.pageNumber--;
        }
        this.updateTable();
    }

    goToLastPage() {
        if (this.hasNextRecords()) {
            this.pageNumber = Math.ceil(this.totalCount / this.pageSize) - 1;
        }
        this.updateTable();
    }

    goToFirstPage() {
        if (this.hasPreviousRecords()) {
            this.pageNumber = 0;
        }
        this.updateTable();
    }

    getFirstNumberForPage() {
        return (this.pageNumber * this.pageSize) + 1;
    }

    getLastNumberForPage() {
        const lastNumber = Number(this.getFirstNumberForPage()) + Number(this.pageSize) - 1
        return lastNumber > this.totalCount ? this.totalCount : lastNumber;
    }
}
