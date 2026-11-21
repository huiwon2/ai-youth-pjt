#!/bin/sh

probe_ipv4() {
    provider=$1
    url=$2

    if address=$(curl --ipv4 --fail --silent --max-time 5 "$url"); then
        case "$address" in
            ''|*[!0-9.]*) ;;
            *) printf 'FIRSTHOME_EGRESS_IPV4 %s %s\n' "$provider" "$address"; return ;;
        esac
    fi
    printf 'FIRSTHOME_EGRESS_PROBE_FAILED %s\n' "$provider"
}

probe_ipv4 ipify https://api.ipify.org
probe_ipv4 aws https://checkip.amazonaws.com

exec java -jar app.jar
