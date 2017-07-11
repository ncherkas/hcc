package com.ncherkas.hazelcast;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.stream.IntStream;

public class AppTest {

  @DataProvider(name = "exceptionOnIllegalCtorArgs")
  public Object[][] getExceptionOnIllegalCtorArgs() {
    return new Object[][] {
      { null, mock(com.typesafe.config.Config.class) },
      { mock(HazelcastInstance.class), null }
    };
  }

  @Test(dataProvider = "exceptionOnIllegalCtorArgs", expectedExceptions = NullPointerException.class)
  public void testExceptionOnIllegalCtorArgs(HazelcastInstance hazelcastInstance, com.typesafe.config.Config appConfig) {
    App app = new App(hazelcastInstance, appConfig);
  }

  @DataProvider(name = "appRunCases")
  public Object[][] getAppRunCases() {
    return new Object[][] {
        { 10, false, 1 }, // 10 instances, not waiting for all to become active
        { 3, true, 3 } // 3 instance, waiting for all 3 to become active
    };
  }

  @Test(dataProvider = "appRunCases")
  public void testAppRun(int clusterSize, boolean waitForClusterToBecomeActive, int appSleepSec) {
    TestHazelcastInstanceFactory hazelcastInstanceFactory = new TestHazelcastInstanceFactory();

    try {
      HazelcastInstance hazelcastTestInstance = hazelcastInstanceFactory.newHazelcastInstance();
      IAtomicLong startSignalsCount = hazelcastTestInstance.getAtomicLong("startSignalsCount");
      startSignalsCount.set(0);

      Config appConfig = ConfigFactory.defaultApplication()
          .withValue("app.cluster.size", ConfigValueFactory.fromAnyRef(clusterSize))
          .withValue("app.cluster.waitToBecomeActive", ConfigValueFactory.fromAnyRef(waitForClusterToBecomeActive))
          .withValue("app.sleepSec", ConfigValueFactory.fromAnyRef(appSleepSec));

      IntStream.range(0, clusterSize).parallel().forEach(idx -> {
        HazelcastInstance hazelcastAppInstance = hazelcastInstanceFactory.newHazelcastInstance();

        App app = spy(new App(hazelcastAppInstance, appConfig));
        doAnswer((invocation) -> {
          System.out.println("We are started!");
          startSignalsCount.incrementAndGet();

          if (waitForClusterToBecomeActive) {
            // We expect clusterSize + 1 as we have one more instance that we use for test purpose
            assertEquals(hazelcastTestInstance.getCluster().getMembers().size(), clusterSize + 1);
          }

          return null;
        }).when(app).signalWeAreStarted();

        app.run();
      });

      // One and only one instance must signal the start
      assertEquals(startSignalsCount.get(), 1);
    } finally {
      hazelcastInstanceFactory.shutdownAll();
    }
  }
}
