#!/usr/bin/env bash
# -------------------------------------------------------------------------
# sonatype-upload.sh – Upload a local Maven repository folder to
# Sonatype Central Portal (https://central.sonatype.com).
#
# This script is build-tool independent and can be called after any
# combination of Gradle / Maven / bnd builds have populated a shared
# release directory.
#
# Uploads release bundles via /api/v1/publisher/upload.
#
# Note: uses `jar cMf` (from JDK) instead of `zip` for git bash compatibility.
#
# Usage:
#   SONATYPE_BEARER=<token> ./.github/scripts/sonatype-upload.sh [options] <release-dir>
#
# Options:
#   --publishing-type <AUTOMATIC|USER_MANAGED>   (default: USER_MANAGED, release only)
#   --name <deployment-name>                     (default: auto-generated)
#   --upload-url <url>                           (default: Sonatype Central Portal release URL)
#
# Environment:
#   SONATYPE_BEARER   – Bearer token for authentication (required)
#
# -------------------------------------------------------------------------
set -euo pipefail

# ---- defaults -----------------------------------------------------------
UPLOAD_URL="https://central.sonatype.com/api/v1/publisher/upload"
PUBLISHING_TYPE="USER_MANAGED"   # USER_MANAGED (manual) or AUTOMATIC
DEPLOYMENT_NAME=""
RELEASE_DIR=""
# -------------------------------------------------------------------------

analyze_versions() {
	local versions
	local snapshot_versions
	local release_versions

	# Derive versions from Maven repository paths:
	#   group/path/artifact/version/artifact-version*.pom
	versions=$(find "${RELEASE_DIR}" -type f -name '*.pom' -print0 | while IFS= read -r -d '' file; do
		rel_path="${file#"${RELEASE_DIR}"/}"
		rel_dir="$(dirname "${rel_path}")"
		version="$(echo "${rel_dir}" | awk -F/ '{print $(NF)}')"

		if [[ -n "${version}" ]]; then
			echo "${version}"
		fi
	done | sed '/^$/d' | awk '!seen[$0]++')

	snapshot_versions=$(printf '%s\n' "${versions}" | grep -- '-SNAPSHOT$' | sed '/^$/d' | paste -sd, - || true)
	release_versions=$(printf '%s\n' "${versions}" | grep -v -- '-SNAPSHOT$' | sed '/^$/d' | paste -sd, - || true)

	if [[ -n "${snapshot_versions}" && -n "${release_versions}" ]]; then
		echo "Error: mixed versions detected in ${RELEASE_DIR}:" >&2
		echo "  SNAPSHOT versions: ${snapshot_versions}" >&2
		echo "  Release versions:  ${release_versions}" >&2
		echo "Please upload snapshots and releases separately." >&2
		exit 1
	fi

	if [[ -n "${snapshot_versions}" ]]; then
		DETECTED_VERSION_MODE="snapshot"
		DETECTED_VERSION_LIST="${snapshot_versions}"
	elif [[ -n "${release_versions}" ]]; then
		DETECTED_VERSION_MODE="release"
		DETECTED_VERSION_LIST="${release_versions}"
	else
		DETECTED_VERSION_MODE="unknown"
		DETECTED_VERSION_LIST=""
	fi
}

detect_groupid() {
	local path_groups

	# Derive groupIds only from Maven repository paths.
	# We intentionally do not inspect arbitrary files or recurse into jar contents.
	path_groups=$(find "${RELEASE_DIR}" -type f \( -name '*.pom' -o -name 'maven-metadata.xml' \) -print0 | while IFS= read -r -d '' file; do
		rel_path="${file#"${RELEASE_DIR}"/}"
		rel_dir="$(dirname "${rel_path}")"

		# Expected Maven paths:
		#   group/path/artifact/version/artifact-version*.pom
		#   group/path/artifact/maven-metadata.xml
		if [[ "${rel_path}" == *.pom ]]; then
			group_path="$(echo "${rel_dir}" | sed -E 's|/[^/]+/[^/]+$||')"
		else
			group_path="$(echo "${rel_dir}" | sed -E 's|/[^/]+$||')"
		fi

		if [[ -n "${group_path}" && "${group_path}" != "${rel_dir}" ]]; then
			echo "${group_path}" | tr '/' '.'
		fi
	done | sed '/^$/d' | awk '!seen[$0]++')
	if [[ -n "${path_groups}" ]]; then
		printf '%s\n' "${path_groups}" | paste -sd, -
		return 0
	fi

	echo "unknown-group"
}

usage() {
	cat <<-EOF
	Usage: $(basename "$0") [options] <release-dir>

	Upload a local Maven repository folder to Sonatype Central Portal.

	Options:
	  --publishing-type <AUTOMATIC|USER_MANAGED>  Publishing type (default: USER_MANAGED)
	  --name <name>                               Deployment name (default: auto-generated)
	  --upload-url <url>                           Release upload endpoint URL
	  -h, --help                                  Show this help message

	Environment:
	  SONATYPE_BEARER   Bearer token for authentication (required)
	EOF
	exit "${1:-0}"
}

# ---- parse arguments ----------------------------------------------------
while [[ $# -gt 0 ]]; do
	case "$1" in
		--publishing-type) PUBLISHING_TYPE="$2"; shift 2 ;;
		--name)            DEPLOYMENT_NAME="$2"; shift 2 ;;
		--upload-url)      UPLOAD_URL="$2"; shift 2 ;;
		-h|--help)         usage 0 ;;
		-*)                echo "Unknown option: $1" >&2; usage 1 ;;
		*)                 RELEASE_DIR="$1"; shift ;;
	esac
done

if [[ -z "${RELEASE_DIR}" ]]; then
	echo "Error: release directory argument is required" >&2
	usage 1
fi

if [[ ! -d "${RELEASE_DIR}" ]]; then
	echo "Error: release directory does not exist: ${RELEASE_DIR}" >&2
	exit 1
fi

: "${SONATYPE_BEARER:?Error: SONATYPE_BEARER environment variable is not set}"

# ---- validate repository version mode -----------------------------------
analyze_versions
echo "Detected version mode: ${DETECTED_VERSION_MODE}"
if [[ -n "${DETECTED_VERSION_LIST}" ]]; then
	echo "Detected versions: ${DETECTED_VERSION_LIST}"
fi

if [[ "${DETECTED_VERSION_MODE}" == "snapshot" ]]; then
	echo "Skipping upload: only SNAPSHOT versions were found in ${RELEASE_DIR}." >&2
	echo "This script uploads release bundles only." >&2
	exit 0
fi

# Deployment ID file stored beside the release dir
DEPLOYMENTID_FILE="${RELEASE_DIR%/}_DEPLOYMENTID.txt"

# ---- build deployment name ----------------------------------------------
if [[ -z "${DEPLOYMENT_NAME}" ]]; then
	GROUP_ID=$(detect_groupid)
	DEPLOYMENT_NAME="uploaded ${GROUP_ID} on $(date '+%Y%m%d-%H%M%S')"
fi

# ---- release deployment: create bundle zip ------------------------------
BUNDLE_ZIP="${TMPDIR:-/tmp}/sonatype-bundle-$$.zip"
rm -f "${BUNDLE_ZIP}"
trap 'rm -f "${BUNDLE_ZIP}"' EXIT

echo "Creating Sonatype Central bundle from ${RELEASE_DIR} ..."
(cd "${RELEASE_DIR}" && jar cMf "${BUNDLE_ZIP}" .)

BUNDLE_SIZE=$(stat -c%s "${BUNDLE_ZIP}" 2>/dev/null || stat -f%z "${BUNDLE_ZIP}")
echo "Bundle size: ${BUNDLE_SIZE} bytes"

if [[ "${BUNDLE_SIZE}" -eq 0 ]]; then
	echo "Error: bundle zip is empty – nothing to upload" >&2
	exit 1
fi

# ---- upload bundle ------------------------------------------------------
# URL-encode the deployment name (handles spaces and special characters)
ENCODED_NAME=$(printf '%s' "${DEPLOYMENT_NAME}" | sed -e 's/%/%25/g' -e 's/ /%20/g' -e 's/&/%26/g' -e 's/=/%3D/g' -e 's/?/%3F/g' -e 's/+/%2B/g' -e 's/#/%23/g')
QUERY="name=${ENCODED_NAME}&publishingType=${PUBLISHING_TYPE}"

echo "Uploading bundle to Sonatype Central Portal ..."
echo "  URL: ${UPLOAD_URL}"
echo "  Publishing type: ${PUBLISHING_TYPE}"
echo "  Name: ${DEPLOYMENT_NAME}"

HTTP_RESPONSE="${TMPDIR:-/tmp}/sonatype-response-$$.txt"
trap 'rm -f "${BUNDLE_ZIP}" "${HTTP_RESPONSE}"' EXIT

HTTP_CODE=$(curl -sS -w '%{http_code}' -o "${HTTP_RESPONSE}" \
	-H "Authorization: Bearer ${SONATYPE_BEARER}" \
	-F "bundle=@${BUNDLE_ZIP}" \
	"${UPLOAD_URL}?${QUERY}")

RESPONSE_BODY=$(cat "${HTTP_RESPONSE}")

if [[ "${HTTP_CODE}" -lt 200 || "${HTTP_CODE}" -ge 300 ]]; then
	echo "Error: Upload failed with HTTP ${HTTP_CODE}" >&2
	echo "${RESPONSE_BODY}" >&2
	exit 1
fi

# The response body contains the deployment ID
DEPLOYMENT_ID="${RESPONSE_BODY}"
echo "Upload accepted. Deployment ID: ${DEPLOYMENT_ID}"

# Store the deployment ID beside the release directory
echo "${DEPLOYMENT_ID}" > "${DEPLOYMENTID_FILE}"
echo "Deployment ID stored in: ${DEPLOYMENTID_FILE}"
