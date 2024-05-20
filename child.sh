#!/bin/bash

# Function to start a process in the background
start_process() {
    ip netns exec clust$1 java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="./config/logback.xml" -cp "build/install/library/lib/*"  bftsmart.demo.counter.CounterServer $1 &
    # Store the process ID (PID) in an array
    pids+=("$!")
    echo $pids
}

# Function to trap exit signals and kill child processes
cleanup() {
    echo "Exiting script..."
    # Kill all child processes
    for pid in "${pids[@]}"; do
        kill "$pid" >/dev/null 2>&1
    done
    exit
}

# Trap exit signals (SIGINT, SIGTERM) and call cleanup function
trap cleanup EXIT

# Array to store process IDs
declare -a pids

# Start multiple processes
echo "Starting processes..."
for ((i = 1; i <= 4; i++)); do
    start_process $i
done

# Wait for all processes to finish
wait

