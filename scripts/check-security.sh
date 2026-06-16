cd ..
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect --source=/repo --verbose
