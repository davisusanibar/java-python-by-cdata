#!/usr/bin/env bash

create_conda_env_for_cdata_java_python() {
  conda create -y -n cdata_java_python -c conda-forge \
  --file src/main/resources/env/requirements.txt \
  python=3.9
}

"$@"