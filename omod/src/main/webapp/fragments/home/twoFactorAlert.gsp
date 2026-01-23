<% if (showBanner) { %>
    <style>
        .two-factor-setup-link {
            color: blue;
            text-decoration: underline;
        }
    </style>
    <div class="note-container">
        <div class="note warning" style="width: 100%;">
            <div class="text">
                <i class="fas fa-fw fa-lock" style="vertical-align: middle;"></i>
                <a id="two-factor-setup-link" class="two-factor-setup-link" href="${ ui.pageLink("authenticationui", "account/twoFactorSetup") }">${ ui.message("authentication.2fa.enableMessage") }</a>
            </div>
        </div>
    </div>
<% } %>

<% if (showDialog) { %>
    <script type="text/javascript">
        jq(document).ready(function() {
            emr.setupConfirmationDialog({
                selector: '#two-factor-popup',
                actions: {
                    confirm: function() {
                        document.location.href = '${ ui.pageLink("authenticationui", "account/twoFactorSetup") }';
                    },
                    cancel: function() {
                    }
                }
            }).show();
        });
    </script>
    <div id="two-factor-popup" class="dialog" style="display:none;">
        <div class="dialog-header">
            <i class="fas fa-fw fa-vial"></i>
            <h3>${ui.message("authenticationui.configure2fa.title")}</h3>
        </div>
        <div class="dialog-content form">
            <div class="row">
                ${ui.message("authentication.2fa.enableMessage")}
            </div>
            <br><br>
            <button class="cancel">${ ui.message("pihapps.notNow") }</button>
            <button class="confirm right">${ ui.message("pihapps.enable") }
                <i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i>
            </button>
        </div>
    </div>
<% } %>
