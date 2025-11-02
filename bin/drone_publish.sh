#!/bin/bash

echo "PUBLISH CODE"

rm -rf .git
git init
git add .
git commit -m "Publish $DRONE_TAG"
git branch -M main
git config user.name aratare-jp
git config user.email 58905307+aratare-jp@users.noreply.github.com
git remote add origin "https://$GH_TOKEN@github.com/aratare-jp/anmo-cli.git"
git push -u origin main

echo "PUBLISH ARTIFACTS"

RELEASE_ID=$(
  curl -L \
  -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/aratare-jp/anmo-cli/releases \
  -d "{\"tag_name\":\"$DRONE_TAG\",\"target_commitish\":\"main\",\"name\":\"$DRONE_TAG\",\"body\":\"Description of the release\",\"draft\":false,\"prerelease\":false,\"generate_release_notes\":false}" \
  | jq -r '.id'
) 
  
curl -sL \
-X POST \
-H "Accept: application/vnd.github+json" \
-H "Authorization: Bearer $GH_TOKEN" \
-H "X-GitHub-Api-Version: 2022-11-28" \
-H "Content-Type: application/octet-stream" \
"https://uploads.github.com/repos/aratare-jp/anmo-cli/releases/$RELEASE_ID/assets?name=anmo-$DRONE_TAG.jar" \
--data-binary "@target/me.suppai/anmo-$DRONE_TAG.jar"

echo "DONE"