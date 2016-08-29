#!/bin/bash
set -e
cd $(dirname $0)
boot public
firebase deploy
