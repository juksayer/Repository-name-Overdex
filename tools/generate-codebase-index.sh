#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
output="$repo_root/CODEBASE_INDEX.md"
temporary="$(mktemp "$repo_root/.codebase-index.XXXXXX")"
trap 'rm -f "$temporary"' EXIT

relative_link() {
    local path="$1"
    local encoded="$path"
    encoded="${encoded//%/%25}"
    encoded="${encoded// /%20}"
    encoded="${encoded//#/%23}"
    encoded="${encoded//(/%28}"
    encoded="${encoded//)/%29}"
    printf -- '- [`%s`](%s)\n' "$path" "$encoded"
}

append_files() {
    local title="$1"
    shift
    local -a files=()
    while IFS= read -r -d '' file; do
        files+=("${file#"$repo_root/"}")
    done < <(find "$@" -type f -print0 2>/dev/null | sort -z)

    printf '## %s (%d)\n\n' "$title" "${#files[@]}" >> "$temporary"
    if ((${#files[@]} == 0)); then
        printf '_None._\n\n' >> "$temporary"
        return
    fi
    local file
    for file in "${files[@]}"; do
        relative_link "$file" >> "$temporary"
    done
    printf '\n' >> "$temporary"
}

append_code_files() {
    local title="$1"
    local root="$2"
    local -a files=()
    while IFS= read -r -d '' file; do
        files+=("${file#"$repo_root/"}")
    done < <(
        find "$root" -type f \( -name '*.kt' -o -name '*.java' \) -print0 2>/dev/null | sort -z
    )

    printf '## %s (%d)\n\n' "$title" "${#files[@]}" >> "$temporary"
    if ((${#files[@]} == 0)); then
        printf '_None._\n\n' >> "$temporary"
        return
    fi
    local file
    for file in "${files[@]}"; do
        relative_link "$file" >> "$temporary"
    done
    printf '\n' >> "$temporary"
}

append_unexpected_source_files() {
    local root="$1"
    local -a files=()
    while IFS= read -r -d '' file; do
        files+=("${file#"$repo_root/"}")
    done < <(
        find "$root" -type f ! -name '*.kt' ! -name '*.java' -print0 2>/dev/null | sort -z
    )

    printf '## Non-source files inside the production code tree (%d)\n\n' "${#files[@]}" >> "$temporary"
    printf 'These files are indexed separately because their placement under `app/src/main/java` needs review.\n\n' >> "$temporary"
    local file
    for file in "${files[@]}"; do
        relative_link "$file" >> "$temporary"
    done
    printf '\n' >> "$temporary"
}

revision="$(git -C "$repo_root" rev-parse --short HEAD 2>/dev/null || printf 'unavailable')"
generated="$(date +%Y-%m-%d)"
asset_count="$(find "$repo_root/app/src/main/assets" -type f 2>/dev/null | wc -l | tr -d ' ')"

cat > "$temporary" <<EOF
# Overdex Codebase Index

**Generated:** $generated

**Base revision:** \`$revision\`

**Scope:** Current working tree, including untracked files

**Generator:** [tools/generate-codebase-index.sh](tools/generate-codebase-index.sh)

This is a generated inventory of source, tests, Android resources, validation
material, and project documentation. It is not the architecture authority; see
[PROJECT-MAP-v2.md](PROJECT-MAP-v2.md) for runtime ownership and system
boundaries.

The asset tree contains **$asset_count files** and is summarized rather than
listed individually because sprite, cry, and reference collections would bury
the source inventory. Asset licensing and catalog files remain discoverable from
the asset roots.

EOF

append_code_files "Production Kotlin and Java" "$repo_root/app/src/main/java"
append_unexpected_source_files "$repo_root/app/src/main/java"
append_code_files "Unit tests" "$repo_root/app/src/test/java"
append_code_files "Instrumentation tests" "$repo_root/app/src/androidTest/java"
append_files "Android resources" \
    "$repo_root/app/src/main/res"
append_files "Validation material" \
    "$repo_root/validation"
append_files "DexDox documentation" \
    "$repo_root/DexDox"

printf '## Root project documents and maintenance scripts\n\n' >> "$temporary"
while IFS= read -r -d '' file; do
    relative_link "${file#"$repo_root/"}" >> "$temporary"
done < <(
    {
        find "$repo_root" -maxdepth 1 -type f \
            \( -name '*.md' -o -name '*.kts' -o -name '*.properties' \) -print0
        find "$repo_root/tools" -maxdepth 1 -type f -print0
        find "$repo_root/gradle" -maxdepth 2 -type f -print0
        printf '%s\0' "$output"
    } | sort -zu
)

mv "$temporary" "$output"
trap - EXIT
printf 'Updated %s\n' "$output"
