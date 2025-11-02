#!/bin/bash

echo "PUBLISH CODE"

echo $(pwd)
rm -rf .git .drone.yml
cd ..
git clone "https://$GH_TOKEN@github.com/aratare-jp/anmo-cli.git" gh-anmo-cli 
cp -R src/. gh-anmo-cli/
cd gh-anmo-cli
ls -lhia
git add .
git commit -m "Publish $DRONE_COMMIT_MESSAGE"
#git branch -M main
#git config user.name aratare-jp
#git config user.email 58905307+aratare-jp@users.noreply.github.com
#git remote add origin "https://$GH_TOKEN@github.com/aratare-jp/anmo-cli.git"
git push origin main --force

echo "DONE"