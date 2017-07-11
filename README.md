# Hazelcast code challenge

## Problem
```
 Imagine an application that is deployed in 10 nodes. A node is a
 separate JVM process, potentially can be running on different physical
 machine.

 Write an application that will be running on 10 nodes. The application
 need to coordinate among the nodes and make sure that one and only one
 of them does a System.out.println("We are started!"). Note: that not all
 10 nodes shall start at the same time.

 It is possible that some will start couple of seconds/minutes later.
 Some will not at all. Still I want this message to be printed and only
 once. 
 You can use an existing library for the solution. No need to build a distributed system from scratch. 

 Providing an elegant solution to the problem is what we seek.
```

## Solution
My solution is a kind of double-checked locking using Hazelcast distributed objects - Lock, IAtomicReference and ICountDownLatch. Depending on value of config parameter "app.cluster.waitToBecomeActive" (see application.properties) application either performs start logic (in our case outputs "We are started!") on a 1st instance that is up and acquired a lock or waits until number of instances set by config param "app.cluster.size" are up and runs start logic after it - on a arbitrary instance that has acquired a lock. By doing this we ensure that start logic will be executed once and only once.

## Testing

### 1. Build instructions
Simply run ```mvn clean install``` in the project directory.

### 2. Unit tests
Unit tests are checking two scenarios:
 - cluster consisting of 10 instances running in parallel, start logic executed by the very first instance which is up and running and which has acquired a lock
 - cluster of 3 instances, start logic gets executed when all 3 instances are up and running - by one of them which has acquired a lock
In unit tests I've used ```TestHazelcastInstanceFactory``` which creates a mock communication protocol instead of tcp network.

### 2. Docker
Solution includes a build-in support to test it within Docker environment.
Instruction to run application within Docker Compose cluster:
 - install [Docker](https://www.docker.com/) 
 - in project directory run run ```maven clean install```
 - run ```mvn package docker:build -Dmaven.test.skip=true``` 
 - if you're using Mac, export environment variable pointing to Docker host:
```
export DOCKER_HOST=unix:///var/run/docker.sock
```
 - export environment variables to override default application settings:
```
export DOCKER_HCC_CLUSTER_SIZE=10
export DOCKER_HCC_WAIT=false
```
 - within "docker" directory run ```docker-compose up --scale hcc-app=$DOCKER_HCC_CLUSTER_SIZE -d``` - this will set up a cluster of 10 machines in detach mode
 - wait a bit and run ```docker-compose logs | grep started!``` to search for the instance that executed start logic - it will be a single instance no matter what is the size of the cluster (I've tested max 50 containers)
 - run ```docker-compose stop ``` to stop the cluster

