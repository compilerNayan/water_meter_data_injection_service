#!/usr/bin/env bash
# Delete ALL items from Water Meter DynamoDB tables (tables are kept).
# Requires: aws cli, jq
#
# Usage:
#   ./scripts/wipe_dynamodb_contents.sh              # interactive confirm
#   ./scripts/wipe_dynamodb_contents.sh --yes      # skip confirm
#   AWS_REGION=ap-south-1 AWS_PROFILE=myprofile ./scripts/wipe_dynamodb_contents.sh --yes
#
set -euo pipefail

REGION="${AWS_REGION:-ap-south-1}"
PROFILE="${AWS_PROFILE:-}"
AUTO_YES=false

for arg in "$@"; do
  case "$arg" in
    --yes|-y) AUTO_YES=true ;;
    -h|--help)
      sed -n '1,12p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (use --yes or --help)" >&2
      exit 1
      ;;
  esac
done

if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI not found in PATH" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq not found in PATH" >&2
  exit 1
fi

aws_args=(--region "$REGION")
if [[ -n "$PROFILE" ]]; then
  aws_args+=(--profile "$PROFILE")
fi

# table_name:key1[:key2...]
TABLE_SPECS=(
  "WaterMeterUsers:userId"
  "WaterMeterTenants:tenantId"
  "WaterMeterDevicePreEnrollments:serialNumber"
  "WaterMeterUnits:unitId"
  "WaterMeterDeviceState:deviceId"
  "WaterMeterTodaySlots:deviceId:slotKey"
  "WaterMeterDayHistory:deviceId:dayKey"
  "WaterMeterDeviceConfig:deviceId"
  "WaterMeterDummyDevices:deviceKey"
  "TestTable:test_id"
)

table_exists() {
  local table="$1"
  aws dynamodb describe-table "${aws_args[@]}" --table-name "$table" >/dev/null 2>&1
}

# Build jq filter: .Items[] -> DeleteRequest with given key attribute names
build_delete_requests_jq() {
  local filter='.Items[] | {DeleteRequest: {Key: {'
  local first=true
  while [[ $# -gt 0 ]]; do
    local key="$1"
    shift
    if [[ "$first" == true ]]; then
      filter+="${key}: .${key}"
      first=false
    else
      filter+=", ${key}: .${key}"
    fi
  done
  filter+='}}}'
  echo "$filter"
}

wipe_table() {
  local spec="$1"
  local old_ifs=$IFS
  IFS=':'
  # shellcheck disable=SC2086
  set -- $spec
  IFS=$old_ifs

  local table_name=$1
  shift
  local key_attrs=("$@")

  if [[ ${#key_attrs[@]} -eq 0 ]]; then
    echo "ERROR  $table_name: could not parse key attributes from spec '$spec'" >&2
    return 1
  fi

  if ! table_exists "$table_name"; then
    echo "SKIP  $table_name (table not found in $REGION)"
    return 0
  fi

  local projection="${key_attrs[0]}"
  local i
  for ((i = 1; i < ${#key_attrs[@]}; i++)); do
    projection+=",${key_attrs[i]}"
  done
  local jq_filter
  jq_filter=$(build_delete_requests_jq "${key_attrs[@]}")

  local last_evaluated_key=""
  local deleted=0

  echo "WIPE  $table_name (keys: $projection)"

  while true; do
    local scan_cmd=(
      aws dynamodb scan
      "${aws_args[@]}"
      --table-name "$table_name"
      --projection-expression "$projection"
      --output json
    )
    if [[ -n "$last_evaluated_key" ]]; then
      scan_cmd+=(--exclusive-start-key "$last_evaluated_key")
    fi

    local scan_out
    scan_out=$("${scan_cmd[@]}")

    local item_count
    item_count=$(echo "$scan_out" | jq '.Items | length')
    if [[ "$item_count" -eq 0 ]]; then
      break
    fi

    local tmp_requests
    tmp_requests=$(mktemp)
    echo "$scan_out" | jq -c "$jq_filter" >"$tmp_requests"

    local batch=()
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      batch+=("$line")
      if [[ ${#batch[@]} -eq 25 ]]; then
        local batch_json
        batch_json=$(printf '%s\n' "${batch[@]}" | jq -s --arg t "$table_name" '{($t): .}')
        aws dynamodb batch-write-item "${aws_args[@]}" --request-items "$batch_json" >/dev/null
        batch=()
      fi
    done <"$tmp_requests"
    rm -f "$tmp_requests"

    if [[ ${#batch[@]} -gt 0 ]]; then
      local batch_json
      batch_json=$(printf '%s\n' "${batch[@]}" | jq -s --arg t "$table_name" '{($t): .}')
      aws dynamodb batch-write-item "${aws_args[@]}" --request-items "$batch_json" >/dev/null
    fi

    deleted=$((deleted + item_count))

    last_evaluated_key=$(echo "$scan_out" | jq -c '.LastEvaluatedKey // empty')
    if [[ -z "$last_evaluated_key" || "$last_evaluated_key" == "null" ]]; then
      break
    fi
  done

  echo "DONE  $table_name ($deleted items deleted)"
}

echo "Region:  $REGION"
if [[ -n "$PROFILE" ]]; then
  echo "Profile: $PROFILE"
fi
echo ""
echo "This will DELETE ALL ROWS from these tables (tables are NOT dropped):"
for spec in "${TABLE_SPECS[@]}"; do
  echo "  - ${spec%%:*}"
done
echo ""

if [[ "$AUTO_YES" != true ]]; then
  read -r -p "Type 'yes' to continue: " confirm
  if [[ "$confirm" != "yes" ]]; then
    echo "Aborted."
    exit 1
  fi
fi

echo ""
for spec in "${TABLE_SPECS[@]}"; do
  wipe_table "$spec"
done

echo ""
echo "All requested table contents wiped."
