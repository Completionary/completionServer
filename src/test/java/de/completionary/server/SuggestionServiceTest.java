package de.completionary.server;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.completionary.proxy.elasticsearch.AnalyticsLogger;
import de.completionary.proxy.elasticsearch.SuggestionIndex;
import de.completionary.proxy.helper.ProxyOptions;
import de.completionary.proxy.helper.Statistics;
import de.completionary.proxy.thrift.services.suggestion.AnalyticsData;
import de.completionary.proxy.thrift.services.suggestion.Suggestion;
import de.completionary.proxy.thrift.services.suggestion.SuggestionService;

/**
 * Don't forget to start the server with mvn jetty:run before running
 * this test!
 */
public class SuggestionServiceTest {

    TTransport transport;

    TProtocol protocol;

    private static String indexID = "";

    //    private static final String serverURL =
    //            "http://metalcon2.physik.uni-mainz.de:"
    //                    + ProxyOptions.SUGGESTION_SERVER_HTTP_PORT
    //                    + "/server-0.0.1";
    private static final String serverURL = "http://localhost:"
            + ProxyOptions.SUGGESTION_SERVER_HTTP_PORT + "/server-0.0.1";

    @Before
    public void setUp() throws TTransportException {
        AnalyticsLogger.disableLogging();
        Random r = new Random();
        indexID = "testindex" + r.nextInt();

        transport = new THttpClient(serverURL);
        protocol = new TJSONProtocol(transport);
    }

    private SuggestionService.Client generateClient() {
        SuggestionService.Client client =
                new SuggestionService.Client(protocol);
        while (true) {
            try {
                transport.open();
                break;
            } catch (TTransportException e) {
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                }
            }
        }
        return client;
    }

    @After
    public void tearDown() throws InterruptedException, ExecutionException {
        transport.close();
        SuggestionIndex.delete(indexID);
    }

    @Test
    public void consistencyTest() throws TException {
        final String term = "asdf";
        final String query = "as";
        final String payload = "{}";

        SuggestionService.Client client = generateClient();

        SuggestionIndex index = SuggestionIndex.getIndex(indexID);
        try {
            index.truncate();

            /*
             * Check if the index is really truncated
             */
            List<Suggestion> suggestions =
                    client.findSuggestionsFor(indexID, query, (short) 10,
                            new AnalyticsData(1, "testUserAgent"));
            Assert.assertTrue(suggestions.isEmpty());

            /*
             * Add an element and check if we find it in a query
             */
            final CountDownLatch lock = new CountDownLatch(1);
            index.async_addSingleTerm(1, Arrays.asList(new String[] {
                term
            }), null, payload, 1, new AsyncMethodCallback<Long>() {

                public void onError(Exception arg0) {

                }

                public void onComplete(Long arg0) {
                    lock.countDown();
                }
            });
            Assert.assertTrue("async_findSuggestionsFor has timed out",
                    lock.await(2000, TimeUnit.MILLISECONDS));

            /*
             * Check if we can find the new term
             */
            suggestions =
                    client.findSuggestionsFor(indexID, query, (short) 10,
                            new AnalyticsData(1, "testUserAgent"));
            Assert.assertEquals(suggestions.size(), 1);
            Assert.assertEquals(suggestions.get(0).suggestion, term);
            Assert.assertEquals(suggestions.get(0).payload, payload);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void speedTest() throws TException, InterruptedException {
        final SecureRandom r = new SecureRandom();

        //        final int numberOfThreads = 100;
        for (int numberOfThreads = 1; numberOfThreads < 200; numberOfThreads *=
                2) {

            final int numberOfQueries = 50;

            for (int i = 0; i < 1000; i++) { // heat up cpu
                r.nextInt();
            }

            final long times[] = new long[numberOfQueries * numberOfThreads];
            final long start = System.nanoTime();

            final AtomicInteger numberOfSuggestionsFound = new AtomicInteger(0);

            Thread[] threads = new Thread[numberOfThreads];
            for (int thread = 0; thread != numberOfThreads; thread++) {
                final int ThreadID = thread;
                threads[thread] = new Thread() {

                    @Override
                    public void run() {
                        TTransport transport;
                        try {
                            transport = new THttpClient(serverURL);
                            TProtocol protocol = new TJSONProtocol(transport);

                            SuggestionService.Client client =
                                    new SuggestionService.Client(protocol);
                            while (true) {
                                try {
                                    transport.open();
                                    break;
                                } catch (TTransportException e) {
                                    e.printStackTrace();
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e1) {
                                    }
                                }
                            }

                            final SecureRandom r = new SecureRandom();
                            for (int i = 0; i < numberOfQueries; i++) {
                                final int queryID = i;
                                String query =
                                        ""
                                                + (char) ('a' + Math.abs(r
                                                        .nextInt()) % 25);

                                try {
                                    //                                wieso brauche ich über thrift nur 4ms, direkt jedoch 5,3...
                                    //                                SuggestionIndex client =
                                    //                                        SuggestionIndex
                                    //                                                .getIndex("wikipediaindex");
                                    final long startTime = System.nanoTime();
                                    List<Suggestion> result =
                                            client.findSuggestionsFor(
                                                    "wikipediaindex", query,
                                                    (short) 15,
                                                    new AnalyticsData());
                                    times[ThreadID * numberOfQueries + queryID] =
                                            System.nanoTime() - startTime;
                                    numberOfSuggestionsFound.addAndGet(result
                                            .size());
                                } catch (TException e) {
                                    e.printStackTrace();
                                }

                            }
                        } catch (TTransportException e1) {
                            e1.printStackTrace();
                        }
                    }
                };
                threads[thread].start();
            }

            for (int thread = 0; thread != numberOfThreads; thread++) {
                threads[thread].join();
            }

            double requestsPerSecond =
                    (double) times.length / (System.nanoTime() - start) * 1E9;

            double averageTimePerRequestms =
                    Statistics.calculateAverage(times) / 1E6;
            double standardDeviation =
                    Statistics.calculateStandardDeviation(times) / 1E6;

            //        System.out.println("Request rate: " + requestsPerSecond + " Hz");
            //        System.out.println("found " + numberOfSuggestionsFound
            //                + " suggestions in " + times.length + " requests) ");
            //        System.out.println("(" + averageTimePerRequestms + "+-"
            //                + standardDeviation + ") ms per request average");
            System.out.println(numberOfThreads + "\t" + averageTimePerRequestms
                    + "\t" + standardDeviation + "\t" + requestsPerSecond);
        }
    }
}
