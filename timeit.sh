while IFS= read -r line; do printf '[%s] %s\n' "$(date +%s%3N)" "$line"; done
