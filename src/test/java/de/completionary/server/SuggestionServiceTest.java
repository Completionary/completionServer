package de.completionary.server;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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

import de.completionary.proxy.elasticsearch.SuggestionIndex;
import de.completionary.proxy.helper.ProxyOptions;
import de.completionary.proxy.thrift.services.suggestion.AnalyticsData;
import de.completionary.proxy.thrift.services.suggestion.Suggestion;
import de.completionary.proxy.thrift.services.suggestion.SuggestionService;

public class SuggestionServiceTest {

    TTransport transport;

    TProtocol protocol;

    private static String indexID = "";

    @Before
    public void setUp() throws TTransportException {
        Random r = new Random();
        indexID = "testindex" + r.nextInt();

        transport =
                new THttpClient("http://localhost:"
                        + ProxyOptions.SUGGESTION_SERVER_HTTP_PORT);
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
    public void perform() throws TException {
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

        final int numberOfQueries = 1000;
        final int numberOfThreads = 1;

        for (int i = 0; i < 1000; i++) { // heat up cpu
            r.nextInt();
        }

        final long randomStartTime = System.currentTimeMillis();
        for (int i = 0; i < numberOfQueries; i++) {
            String query = "" + (char) ('a' + Math.abs(r.nextInt()) % 25);
        }
        final long randomTime = (System.currentTimeMillis() - randomStartTime);

        final float times[] = new float[numberOfQueries];
        final long totalTimeStart = System.currentTimeMillis();

        Thread[] threads = new Thread[numberOfThreads];
        for (int thread = 0; thread != numberOfThreads; thread++) {

            threads[thread] = new Thread() {

                @Override
                public void run() {
                    TTransport transport;
                    try {
                        transport =
                                new THttpClient(
                                        "http://localhost:"
                                                + ProxyOptions.SUGGESTION_SERVER_HTTP_PORT);
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
                            final long startTime = System.currentTimeMillis();
                            try {
                                List<Suggestion> suggestions =
                                        client.findSuggestionsFor(indexID,
                                                query, (short) 15,
                                                new AnalyticsData());
                            } catch (TException e) {
                                e.printStackTrace();
                            }
                            float time =
                                    (System.currentTimeMillis() - startTime);
                            times[queryID] = time;
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

        float time =
                (System.currentTimeMillis() - totalTimeStart - randomTime)
                        * 1000 / (float) numberOfQueries;
        System.out.println("Average per query time: " + time + " µs");
        for (float f : times) {
            System.out.println(f);
        }
    }
}
