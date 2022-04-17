#!/bin/bash

sudo gem install asciidoctor --version 1.5.8

# Don't create gh-pages from dirty working directory
if [ "`git status -s`" ]
then
    echo "The working directory is dirty. Please commit any pending changes."
    exit 1;
fi

# Remember current commit hash
GIT_COMMIT_HASH=$(git rev-parse HEAD)

# Mount the gh-pages branch in a git worktree
git config remote.origin.fetch +refs/heads/*:refs/remotes/origin/*;
git fetch --unshallow origin gh-pages
GH_PAGES=$(mktemp --directory --dry-run --tmpdir kilt_gh-pages.XXXXXXXXX)
git worktree add -b gh-pages "$GH_PAGES" origin/gh-pages

# Convert the asciidoc sources to HTML
asciidoctor \
  --verbose \
  --backend=html5 \
  --attribute=nofooter \
  --attribute="revdate=$(date -Idate)" \
  --destination-dir="$GH_PAGES" \
  --source-dir=docs \
  docs/*.adoc
#  Unsetting author and email doesn't work \
#asciidoctor \
#  --backend=html5 \
#  --attribute=nofooter \
#  --attribute='!author'                  # do not print the author name \
#  --attribute='!email'                   # do not print the email address \
#  --attribute="revnumber=$KILT_VERSION" # specify the kilt revision \
#  --attribute="revdate=$(date -Idate)"  # set the current date \
#  --destination-dir="$GH_PAGES" \
#  --source-dir=docs \
#  docs/*.adoc

# Copy the additional resources to the target directory
pushd docs
find . -type f -not -name "*.adoc" -exec cp '{}' "$GH_PAGES"/'{}' ';'
popd

# Commit the new content
pushd "$GH_PAGES"
git add --all
git config user.name "github-actions@github.org";
git config user.email "Github Actions";
git commit -m "[build_gh-pages.sh] Update gh-pages from ""$GIT_COMMIT_HASH"

# Push content
git push https://x-access-token:${GITHUB_ACCESS_TOKEN}@github.com/hupfdule/kilt.git

# Remove git worktree
popd
# travis' version of git is too old to support 'worktree remove'
#git worktree remove "$GH_PAGES"
git worktree prune
