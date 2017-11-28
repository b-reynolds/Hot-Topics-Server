package io.benreynolds.hottopics.server;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * {@code TimerTest} implements various JUnit test methods that test the functionality of the {@code Timer} class.
 */
public class TimerTest {

    /** Default {@code Timer} interval value test methods will use. */
    private static final double DEFAULT_INTERVAL = 2.5;

    /** {@code Timer} reference that will be used in the test methods. */
    private Timer mTimer;

    /**
     * Instantiates a new {@code Timer} instance using the default interval value and starts it.
     */
    @Before
    public void setUp()
    {
        mTimer = new Timer(DEFAULT_INTERVAL);
        mTimer.start();
    }

    /**
     * '{@code Timer}'s interval value can be set.
     */
    @Test
    public void testIntervalCanBeSet()
    {
        final double desiredInterval = 2.5;
        mTimer.setInterval(desiredInterval);
        Assert.assertEquals(desiredInterval, mTimer.getInterval(), 0.0);
    }

    /**
     * '{@code Timer}'s interval value cannot be negative and only absolute values are set.
     */
    @Test
    public void testIntervalCannotBeNegative()
    {
        mTimer.setInterval(-DEFAULT_INTERVAL);
        Assert.assertTrue(Double.doubleToRawLongBits(mTimer.getInterval()) > 0);
        Assert.assertEquals(DEFAULT_INTERVAL, mTimer.getInterval(), 0.0);
    }

    /**
     * '{@code Timer}'s {@code hasElapsed()} method will return {@code true} if the specified interval has passed.
     */
    @Test
    public void testTimerElapses(){
        Assert.assertFalse(mTimer.hasElapsed());
        try {
            Thread.sleep((long) (DEFAULT_INTERVAL * 1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assert.assertTrue(mTimer.hasElapsed());
    }

}
