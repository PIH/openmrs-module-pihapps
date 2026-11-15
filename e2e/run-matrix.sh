#!/usr/bin/env bash
# Preliminary helper. Runs the suite for one site twice: with the "Enter results by Lab ID" patient action configured
# (on), then with it removed (off). Validation on/off is switched by the specs themselves. The site's appframework
# configuration is restored at the end.
#
# Usage: E2E_SITES=<site> E2E_APPFRAMEWORK_DIR=<dir> E2E_RESTART_CMD='<command>' ./run-matrix.sh [on|off ...]
#   E2E_APPFRAMEWORK_DIR  the running server's openmrs_config/appframework directory (read at startup)
#   E2E_RESTART_CMD       restarts that server, e.g. 'docker restart my-openmrs'
#   E2E_URL / E2E_<SITE>_URL as for the suite (default http://localhost:8080/openmrs)
set -euo pipefail
cd "$(dirname "$0")"
: "${E2E_SITES:?set E2E_SITES to one site}" "${E2E_APPFRAMEWORK_DIR:?}" "${E2E_RESTART_CMD:?}"
case "$E2E_SITES" in *,*) echo "run-matrix.sh runs one site at a time" >&2; exit 1;; esac
SITE_VAR="E2E_$(echo "$E2E_SITES" | tr 'a-z-' 'A-Z_')_URL"
URL="${!SITE_VAR:-${E2E_URL:-http://localhost:8080/openmrs}}"
DIR="$E2E_APPFRAMEWORK_DIR"
EXT="$DIR/e2e_labOrderListActions_extension.json"
BACKUP="$(mktemp -d)"
cp -a "$DIR/." "$BACKUP/"

restore() { rm -f "$EXT"; cp -a "$BACKUP/." "$DIR/"; }
trap restore EXIT

action_json() {
  cat <<'JSON'
[
  {
    "id": "e2e.lab.enterResults",
    "extensionPointId": "org.openmrs.module.pihapps.labOrderList.patientGroupActions",
    "type": "link",
    "label": "Enter results by Lab ID",
    "order": 5,
    "requiredPrivilege": "Add Encounters",
    "extensionParams": {
      "iconClass": "fas fa-fw fa-clipboard-list",
      "jsFunction": "pihappsLabEnterResults",
      "visibleForOrderStatuses": ["COLLECTED"]
    }
  }
]
JSON
}

# Removes every patient action that calls pihappsLabEnterResults from the site's own extension files
strip_action() {
  python3 - "$DIR" <<'PY'
import json, pathlib, sys
for p in pathlib.Path(sys.argv[1]).rglob('*.json'):
    try: d = json.loads(p.read_text())
    except Exception: continue
    if not isinstance(d, list): continue
    keep = [e for e in d if not (isinstance(e, dict) and (e.get('extensionParams') or {}).get('jsFunction') == 'pihappsLabEnterResults')]
    if len(keep) != len(d): p.write_text(json.dumps(keep, indent=2))
PY
}

restart() {
  eval "$E2E_RESTART_CMD" >/dev/null
  for i in $(seq 1 120); do
    c=$(curl -s -o /dev/null -w '%{http_code}' -u "admin:${E2E_ADMIN_PASSWORD:-Admin123}" "$URL/ws/rest/v1/pihapps/labOrder?limit=1" || true)
    [ "$c" = 200 ] && break; sleep 10
  done
  echo "[matrix] $URL $c"
}

for m in ${@:-on off}; do
  echo "[matrix] Lab ID action $m"
  cp -a "$BACKUP/." "$DIR/"; rm -f "$EXT"
  if [ "$m" = on ]; then strip_action; action_json > "$EXT"; else strip_action; fi
  restart
  LABID_ACTION=$m npx playwright test --output="test-results-labid-$m" --reporter=list,html || true
  rm -rf "playwright-report-labid-$m"; mv playwright-report "playwright-report-labid-$m"
done

echo "[matrix] restoring the site's configuration"
restore; trap - EXIT
restart
