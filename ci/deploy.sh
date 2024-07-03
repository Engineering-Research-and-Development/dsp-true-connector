#!/bin/bash

echo "Creating Docker Image..."
sudo docker build -f Dockerfile -t rdlabengpa/dsp_true_connector:test .
cd ..
echo "Image is ready"
echo "Starting deployment to Docker Hub"
sudo docker login -u ${DOCKER_USER} -p ${DOCKER_PASSWORD}
sudo docker push rdlabengpa/dsp_true_connector:test
echo "Image deployed to Docker Hub"