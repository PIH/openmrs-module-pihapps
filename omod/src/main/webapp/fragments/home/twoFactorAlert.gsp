<% if (showTwoFactorAlert) { %>
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
                <a class="two-factor-setup-link" href="${ ui.pageLink("authenticationui", "account/twoFactorSetup") }">${ ui.message("authentication.2fa.enableMessage") }</a>
            </div>
        </div>
    </div>
<% } %>