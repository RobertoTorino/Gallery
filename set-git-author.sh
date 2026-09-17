#!/bin/bash

# Set your Git author config for this repository.

git config user.name "Philip"
git config user.email "RobertoTorino@users.noreply.github.com"

echo "Git author is now set. Your current settings are:"
printf "Username: " && git config user.name
printf "Email: " && git config user.email
