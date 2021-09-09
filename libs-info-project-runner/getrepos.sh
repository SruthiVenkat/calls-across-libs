cd projects
pwd
for row in $(cat ../repos.json  | jq -r '.[] | @base64'); do
    _jq() {
     echo ${row} | base64 --decode | jq -r ${1}
    }
    git clone $(_jq '.scmURL') --branch $(_jq '.selectedTag') --single-branch
done