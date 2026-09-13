# GitHub Integration & Sync

GitHub is the system of record for the Pull Request; this product is a
review layer on top of it, not a replacement. This capability imports
everything needed to review a PR (repo, revisions, diffs, existing
comments, review state, permissions) and syncs review actions taken here
back onto the GitHub PR (comments, approval/request-changes, resolved
discussions, viewed-file state) so the PR stays fully usable outside this
product.

See Epic #3 for the full vision and scope boundary.

## Features

- `github_authentication.feature` — connecting a GitHub account
- `repository_and_pull_request_selection.feature` — selecting a repo and PR to review
- `import_pull_request_content.feature` — importing revisions, commits, files, diffs
- `import_review_data_and_permissions.feature` — importing existing review comments, state, permissions
- `comment_note_storage_and_sync.feature` — storing comments/private notes, excluding notes from sync
- `sync_comments_to_github.feature` — pushing comments back to the GitHub PR
- `sync_review_decision_to_github.feature` — pushing approve/request-changes back to GitHub
- `sync_resolved_discussions_and_viewed_files.feature` — syncing resolved-discussion and viewed-file state
