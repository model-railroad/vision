#!/bin/bash

DRY_RUN="echo"
CONFIG="config.ini"
PLAYLIST_ID=""
PLAYLIST_DIR=""
DL_PLAYLIST="yes"
YOUTUBE_DL="youtube-dl"
FORMAT="--format 299" # mp4 1920x1080

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
            --keep-playlist )
                # Keep LOG playlist if present (for debugging purposes). Default is to refresh it.
                DL_PLAYLIST=""
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
    if [[ -n "$DRY_RUN" ]]; then
        echo "## Dry-run mode. $YOUTUBE_DL does not actually download videos, only metadata."
        LOG="_dry_${LOG}"
        INDEX="_dry_${INDEX}"
    fi
    # For debugging
    # OPT="$OPT --playlist-start 1 --playlist-end 3"

    if [[ ! -f "$LOG" || -n $DL_PLAYLIST ]]; then
        echo "## Downloading playlist $PLAYLIST_ID"
        "$YOUTUBE_DL" --skip-download --id --yes-playlist "$URL" | tee "$LOG"
    fi

    echo "# " $(date) > "$INDEX"
    N_FOUND=0
    N_NEW=0
    for v in $(sed -n '/\[youtube\] [0-9A-Za-z_-]\+: Download/s/.*\] \(.*\):.*/\1/p' "$LOG" | uniq); do
        a=( $(find . -name "*$v*") )
        F="${a[0]}"
        F="${F##./}"    # remove ./ prefix if present
        if [[ -f "$F" ]]; then
            echo "Existing  : $F"
            echo "$F" >> "$INDEX"
            N_FOUND=$((N_FOUND+1))
        else
            echo "New video : $v"
            $DRY_RUN "$YOUTUBE_DL" $FORMAT --id --no-playlist "https://youtu.be/$v"
            a=( $(find . -name "*$v*") )
            F="${a[0]}"
            F="${F##./}"    # remove ./ prefix if present
            if [[ -f "$F" ]]; then
                echo "Downloaded: $F"
                echo "$F" >> "$INDEX"
            fi
            N_NEW=$((N_NEW+1))
        fi
    done
    [[ -n "$DRY_RUN" ]] && echo "# Dry-run mode. Estimate which videos would be downloaded:"
    echo "Found $N_FOUND existing video IDs."
    echo "Found $N_NEW new video IDs."
    echo "Index: " $(wc --lines "$INDEX" | cut -d " " -f 1 ) " files"
}

parse_flags "$@"
parse_config
( do_download )

if [[ -n "$DRY_RUN" ]]; then echo "## DRY-RUN mode. Use -f to actually run." ; fi
