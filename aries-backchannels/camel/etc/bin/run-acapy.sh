#!/bin/bash

echo "Starting agent with: $@"

exec /usr/local/bin/aca-py "$@"
