#!/bin/bash
set -e
cd $(dirname "$0")
cd .

DRY=echo
if [[ "$1" == "-f" ]]; then
	DRY=""
	set -x
else
	echo ; echo "#### DRY-RUN MODE. Use -f to run. ####" ; echo
fi

ROOT=.
GIT_USER=$(sed -n '/email = /s/.*= \(.*\)@.*/\1/p' ~/.gitconfig)
if [[ -z $GIT_USER ]]; then set +x; echo "Git user not found"; exit 1; fi

if [[ ! -d $ROOT/LibUtils ]]; then
  $DRY git submodule add git@github.com:$GIT_USER/libutils.git $ROOT/LibUtils
fi

$DRY git submodule update --init $ROOT/LibUtils
LIB_BRANCH="android-lib-v2"
(   $DRY cd $ROOT/LibUtils
    if ! git branch | grep -q $LIB_BRANCH ; then
        $DRY git branch --track $LIB_BRANCH origin/$LIB_BRANCH
        $DRY git checkout $LIB_BRANCH
    fi
)

