#!/usr/bin/env bash
# Snapshots Athena's representation of every corpus PR (evaluation/corpus.tsv) into
# evaluation/snapshots/<athena-short-sha>/<id>/, plus the conventional GitHub diff
# (pr.diff) for side-by-side comparison. Read-only toward the evaluated repositories:
# Athena fetches public revisions anonymously and is never given a GitHub token; the
# only authenticated call is `gh pr diff`, a GET.
#
# Usage: evaluation/tools/run-corpus.sh [corpus-id ...]   (default: every entry)
set -euo pipefail
cd "$(dirname "$0")/../.."

athena_sha=$(git rev-parse --short HEAD)
out_root="evaluation/snapshots/$athena_sha"
cp_file=$(mktemp)
trap 'rm -f "$cp_file"' EXIT

mvn -q -pl athena-app -am -DskipTests install dependency:build-classpath -Dmdep.outputFile="$cp_file"
classpath="athena-app/target/classes:$(cat "$cp_file")"

tail -n +2 evaluation/corpus.tsv | while IFS=$'\t' read -r id category repository pr base head merge title; do
    if [[ $# -gt 0 && ! " $* " =~ " $id " ]]; then
        continue
    fi
    dir="$out_root/$id"
    mkdir -p "$dir"
    echo "== $id ($category) $repository#$pr"
    gh pr diff "$pr" -R "$repository" > "$dir/pr.diff"
    java -Xmx6g -cp "$classpath" evaluation/tools/AthenaSnapshot.java \
        "https://github.com/$repository.git" "$base" "$head" "$title" "$dir" \
        > "$dir/stdout.log" 2>&1 || echo "   FAILED — see $dir/stdout.log"
done
