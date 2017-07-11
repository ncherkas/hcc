package com.ncherkas.hazelcast;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicReference;
import com.hazelcast.core.ICountDownLatch;
import com.hazelcast.core.ILock;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solution to the Hazelcast code challenge. It's a kind of double-checked locking using
 * Hazelcast distributed objects - Lock, IAtomicReference and ICountDownLatch.
 * Depending on value of config parameter "app.cluster.waitToBecomeActive" (see application.properties)
 * application either performs start logic (in our case outputs "We are started!") on a 1st instance
 * that is up and acquired a lock or waits until number of instances set by config param "app.cluster.size"
 * are up and runs start logic after it - on a arbitrary instance that has acquired a lock.
 */
public class App {

  private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

  private final com.typesafe.config.Config appConfig;
  private final HazelcastInstance hazelcastInstance;

  /**
   * App ctor.
   * @param hazelcastInstance Hazelcast instance
   * @param appConfig application config
   */
  public App(HazelcastInstance hazelcastInstance, com.typesafe.config.Config appConfig) {
    this.hazelcastInstance = checkNotNull(hazelcastInstance);
    this.appConfig = checkNotNull(appConfig);
  }

  /**
   * Runs the application. Please see application.properties for default config.
   * Note that config properties can be overrided using Java system properties.
   */
  public void run() {
    try {
      if (appConfig.getBoolean("app.cluster.waitToBecomeActive")) {
        LOGGER.debug("Waiting for the cluster to become active...");
        waitForClusterToBecomeActive();
      }

      LOGGER.debug("Starting instance {}", hazelcastInstance.getName());
      start();

      // Sleeping as if we are doing some important work
      SECONDS.sleep(appConfig.getLong("app.sleepSec"));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted on application start", ex);
    }
  }

  private void waitForClusterToBecomeActive() throws InterruptedException {
    int clusterSize = appConfig.getInt("app.cluster.size");
    ICountDownLatch clusterStartCountDownLatch = hazelcastInstance.getCountDownLatch("clusterStartCountDown");
    clusterStartCountDownLatch.trySetCount(clusterSize);
    clusterStartCountDownLatch.countDown();
    LOGGER.debug("{} cluster instances active so far", clusterSize - clusterStartCountDownLatch.getCount());
    clusterStartCountDownLatch.await(appConfig.getLong("app.cluster.waitTimeoutSec"), SECONDS);
  }

  private void start() throws InterruptedException {
    IAtomicReference<Boolean> isStarted = hazelcastInstance.getAtomicReference("isStarted");

    if (!TRUE.equals(isStarted.get())) {
      ILock startLock = hazelcastInstance.getLock("startLock");
      long lockWaitTimeSec = appConfig.getLong("app.lock.waitTimeSec");
      long lockLeaseTimeSec = appConfig.getLong("app.lock.leaseTimeSec");

      startLock.tryLock(lockWaitTimeSec, SECONDS, lockLeaseTimeSec, SECONDS);

      try {
        if (!TRUE.equals(isStarted.get())) {
          signalWeAreStarted();
          isStarted.set(TRUE);
        }
      } finally {
        try {
          startLock.unlock();
        } catch (IllegalMonitorStateException ex) {
          LOGGER.warn("Critical section guarantee can be broken");
        }
      }
    }
  }

  @VisibleForTesting
  void signalWeAreStarted() {
    LOGGER.info("We are started!");
  }

  /**
   * Application entry point.
   * @param args args
   */
  public static void main(String[] args) {
    com.typesafe.config.Config appConfig = ConfigFactory.load();
    LOGGER.debug("Running with application config: {}", appConfig);

    Config hazelcastConfig = new Config();

    HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastConfig);

    App app = new App(hazelcastInstance, appConfig);
    app.run();

    LOGGER.info("Stopping application...");
    System.exit(0);
  }
}
