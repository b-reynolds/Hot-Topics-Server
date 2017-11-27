package io.benreynolds.hottopics;

import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

/**
 * {@code TrendManager} uses the Twitter4J Twitter API library to manage the retrieval of Twitter trend information.
 */
class TrendManager {

    // TODO: Extend TrendManager such that it can handle the retrieval of trends from varying locations.

    /**
     * Twitter API credentials.
     */
    private static final String TWITTER_API_CONSUMER_KEY = "";
    private static final String TWITTER_API_CONSUMER_SECRET = "";
    private static final String TWITTER_API_ACCESS_TOKEN = "";
    private static final String TWITTER_API_ACCESS_TOKEN_SECRET = "";

    /**
     * Where On Earth Identifier (WOEID) that represents the location that will be searched for trends.
     */
    private static final int WOEID = 44418; // Greater London

    /**
     * The Twitter API limits the amount of trend requests that can be made to 75 per 15 minutes.
     */
    private static final int MAX_REQUESTS_PER_MINUTE = 4;
    private static final int SECONDS_IN_MINUTE = 60;

    /**
     * Timer used to ensure that no more than {@code MAX_REQUESTS_PER_MINUTE} requests are made to the Twitter API.
     */
    private final Timer mRequestLimiter;

    /**
     * Used to interact with the Twitter API.
     */
    private final Twitter mTwitter4J;

    /**
     * Holds the latest trend information.
     */
    private Trends mTrends;

    /**
     * Instantiates the {@code TrendManager}, sets up Twitter4J and .
     */
    TrendManager() {
        // Instantiate and set up Twitter4J using the supplied credentials.
        mTwitter4J = TwitterFactory.getSingleton();
        mTwitter4J.setOAuthConsumer(TWITTER_API_CONSUMER_KEY, TWITTER_API_CONSUMER_SECRET);
        mTwitter4J.setOAuthAccessToken(new AccessToken(TWITTER_API_ACCESS_TOKEN, TWITTER_API_ACCESS_TOKEN_SECRET));

        // Set the '{@code Timer}'s interval such that the trend request limit will not be exceeded.
        mRequestLimiter = new Timer(SECONDS_IN_MINUTE / MAX_REQUESTS_PER_MINUTE);
    }

    /**
     * Returns a {@code Trends} instance containing current '{@code Trend}'s in {@code WOEID}.
     * @return {@code Trends} instance containing current '{@code Trend}'s in {@code WOEID}. If an exception occurs,
     * returns {@code null}.
     */
    Trends getTrends() {
        // If no trends are currently cached or those that are cached require updating.
        if(mTrends == null || mRequestLimiter.hasElapsed()) {
            try {
                mTrends = mTwitter4J.getPlaceTrends(WOEID);
            }
            catch(TwitterException exception) {
                // There was an error in retrieving the latest trends, continue to use the cached trends.
                exception.printStackTrace();
            }
            // Reset the timer so that trends are not received again until the set period has passed.
            mRequestLimiter.start();
        }
        return mTrends;
    }

}
