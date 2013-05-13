#!/bin/bash
# 
# Simple deploy script
#

# Fail on any error
set -e

HOST="$1"
#this magical command makes BACKEND default to "cassandra", if $2 isn't provided.
BACKEND="${2-cassandra}"
USER=ubuntu
BIN_DIR=`dirname $0`
CONF_DIR=${BIN_DIR}/../conf
BUILD_DIR=${BIN_DIR}/../build
TARFILE=`ls ${BUILD_DIR}/distributions/datahub-*.tgz`
TARFILE=`basename ${TARFILE}`
DISTDIR=`basename ${TARFILE} .tgz`

if [[ "$HOST" == "" || ("$BACKEND" != "cassandra" && "$BACKEND" != "memory") ]] ; then
	echo "Usage: $0 <host> [cassandra|memory]"
	exit 1
fi

echo Shutting down any running datahub instances on ${HOST}...
ssh ${USER}@${HOST} "sudo stop datahub"

echo Deploying ${TARFILE} to ${HOST}
rsync -avv --progress ${BUILD_DIR}/distributions/${TARFILE} ${USER}@${HOST}:/home/${USER}/

echo Exploding tarball...
ssh ${USER}@${HOST} "tar -xzf /home/${USER}/${TARFILE}"

echo Creating symlink
ssh ${USER}@${HOST} "rm /home/${USER}/datahub; ln -s /home/${USER}/${DISTDIR} /home/${USER}/datahub"

PROPERTIES_FILENAME="datahub.properties"
if [ "$BACKEND" == "memory" ] ; then
  PROPERTIES_FILENAME="datahub-memory.properties"
fi

echo Using properties file: $PROPERTIES_FILENAME

echo Installing properties file
if [[ "$HOST" == *dev* ]] ; then
	rsync -avv --progress ${CONF_DIR}/dev/${PROPERTIES_FILENAME} ${USER}@${HOST}:/home/${USER}/datahub/datahub.properties
elif [[ "$HOST" == *staging* ]] ; then
	rsync -avv --progress ${CONF_DIR}/staging/${PROPERTIES_FILENAME} ${USER}@${HOST}:/home/${USER}/datahub/datahub.properties
fi

echo Installing upstart script
rsync -avv --progress ${CONF_DIR}/upstart/datahub.conf ${USER}@${HOST}:/tmp
ssh ${USER}@${HOST} "sudo mv /tmp/datahub.conf /etc/init/"

echo Starting up datahub...
ssh ${USER}@${HOST} "sudo start datahub"

echo Waiting for service to be active...
for i in `seq 1 60` ; do
	curl --silent http://${HOST}:8080 > /dev/null
	if [ "$?" == "0" ] ; then
		echo Service is up.
		exit 0
	fi
	echo -n .
	sleep 1
done
echo Timeout waiting for service to start.
exit 1

