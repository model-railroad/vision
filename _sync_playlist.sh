#!/bin/bash

DRY_RUN="echo"
CONFIG="config.ini"
PLAYLIST_ID=""
PLAYLIST_DIR=""
YOUTUBE_DL="youtube-dl"

function die() {
    echo "Error: $*"
    exit 1
}

function parse_flags() {
    while [ -n "$1" ]; do
		case "$1" in
			-f | --f )
				DRY_RUN=""
				;;
            -c | --config )
                CONFIG="$2"
                shift
                ;;
            -i | --id )
                PLAYLIST_ID="$2"
                shift
                ;;
            -d | --dir )
                PLAYLIST_DIR="$2"
                shift
                ;;
            -h | --help | -help )
                echo
                echo "Usage: $0 [-f] [-h] [--config config] [--id id] [--dir dir]"
                echo
                exit 0
                ;;
        esac
        shift
    done
}

function parse_config() {
    [[ -f "$CONFIG" ]] || die "Missing config file at $CONFIG"
   
    [[ -n "$PLAYLIST_ID" ]] || PLAYLIST_ID=$( sed -n -e 's/playlist_id=\(.*\)/\1/p' "$CONFIG" )
    [[ -n "$PLAYLIST_ID" ]] || die "Missing playlist_id in $CONFIG or via --id"

    [[ -n "$PLAYLIST_DIR" ]] || PLAYLIST_DIR=$( sed -n -e 's/playlist_dir=\(.*\)/\1/p' "$CONFIG" )
    [[ -d "$PLAYLIST_DIR" ]] || die "Invalid output directory: $PLAYLIST_DIR"

    Y=$( sed -n -e 's/youtube_dl=\(.*\)/\1/p' "$CONFIG" )
    if [[ -x "$Y" ]]; then YOUTUBE_DL="$Y"; fi

    echo "Playlist id : $PLAYLIST_ID"
    echo "Playlist dir: $PLAYLIST_DIR"
}

function do_download() {
    cd "$PLAYLIST_DIR"
    pwd
   
    LOG="_download.log"
    INDEX="_index.txt"
    URL="http://www.youtube.com/playlist?list=$PLAYLIST_ID"
    OPT=""
    if [[ -n "$DRY_RUN" ]]; then
        OPT="--skip-download"
        echo "## Dry-run mode. $YOUTUBE_DL does not actually download videos, only metadata."
    fi
    # For debugging
    # OPT="$OPT --playlist-start 1 --playlist-end 3"
    
    echo "## Downloading playlist $PLAYLIST_ID"
    "$YOUTUBE_DL" $OPT --id --yes-playlist "$URL" | tee "$LOG"

    if [[ -z "$DRY_RUN" ]]; then
        echo "# " $(date) > "$INDEX"
        N=0
        # Filter 1: grep "\[download\] Destination:" $LOG | sed -n -e 's/.*: \(.*\)/\1/p'
        # Filter 2: grep "\[download\] .* has already been downloaded" $LOG | sed -n -e 's/.* \(.*\.mp4\).*/\1/p'
        for f in $(  grep ".mp4" "$LOG" | sed -n -e 's/.* \(.*\.mp4\).*/\1/p' | uniq ); do
            if [[ -f "$f" ]]; then
                echo "$f" >> "$INDEX"
                N=$((N+1))
            fi
        done
        echo "Index: $N files"
    else
        echo "# Dry-run mode. Estimate which videos would be downloaded:"
        N_FOUND=0
        N_NEW=0
        for v in $(sed -n '/\[youtube\] [0-9A-Za-z_-]\+: Download/s/.*\] \(.*\):.*/\1/p' "$LOG" | uniq); do
            a=( $(find . -name "*$v*") )
            if [[ -f "${a[0]}" ]]; then
                N_FOUND=$((N_FOUND+1))
            else
                echo "New video ID: $v"
                N_NEW=$((N_NEW+1))
            fi
        done
        echo "Found $N_FOUND existing video IDs."
        echo "Found $N_NEW new video IDs."
    fi
}

parse_flags "$@"
parse_config
( do_download )

if [[ -n "$DRY_RUN" ]]; then echo "## DRY-RUN mode. Use -f to actually run." ; fi
