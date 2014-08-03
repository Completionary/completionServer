package de.completionary.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.completionary.proxy.elasticsearch.SuggestionIndex;
import de.completionary.proxy.helper.ProxyOptions;
import de.completionary.proxy.thrift.services.suggestion.Suggestion;
import de.completionary.proxy.thrift.services.suggestion.SuggestionService;

public class SuggestionServiceTest {

	TTransport transport;

	SuggestionService.Client client;

	private static String indexID = "";

	@Before
	public void setUp() throws Exception {
		Random r = new Random();
		indexID = "testindex" + r.nextInt();

		/*
		 * Start the thrift server in a new thread
		 */
		(new SuggestionServer()).start();

		transport = new TFramedTransport(new TSocket("localhost",
				ProxyOptions.SUGGESTION_SERVER_PORT));
		TProtocol protocol = new TBinaryProtocol(transport);

		client = new SuggestionService.Client(protocol);
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
	}

	@After
	public void tearDown() throws Exception {
		transport.close();
		SuggestionIndex.delete(indexID);
	}

	@Test
	public void perform() throws TException {
		final String term = "asdf";
		final String query = "as";
		final String payload = "{}";

		SuggestionIndex index = SuggestionIndex.getIndex(indexID);
		try {
			index.truncate();

			/*
			 * Check if the index is really truncated
			 */
			List<Suggestion> suggestions = client.findSuggestionsFor(indexID,
					query, (short) 10);
			Assert.assertTrue(suggestions.isEmpty());

			/*
			 * Add an element and check if we find it in a query
			 */
			final CountDownLatch lock = new CountDownLatch(1);
			index.async_addSingleTerm("1",
					Arrays.asList(new String[] { term }), null, payload, 1,
					new AsyncMethodCallback<Long>() {

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
			suggestions = client.findSuggestionsFor(indexID, query, (short) 10);
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

	// @Test
	// public void speedTest() {
	// SuggestionIndex client = new SuggestionIndex("index");
	//
	// Random r = new Random();
	//
	// final int numberOfQueries = 1000;
	// for (int i = 0; i < 1000; i++) { // heat up cpu
	// r.nextInt();
	// }
	//
	// final long randomStartTime = System.currentTimeMillis();
	// for (int i = 0; i < numberOfQueries; i++) {
	// String query = "" + (char) ('a' + Math.abs(r.nextInt()) % 25);
	// }
	// final long randomTime = (System.currentTimeMillis() - randomStartTime);
	//
	// final float times[] = new float[numberOfQueries];
	// final long totalTimeStart = System.currentTimeMillis();
	// for (int i = 0; i < numberOfQueries; i++) {
	// final int queryID = i;
	// String query = "" + (char) ('a' + Math.abs(r.nextInt()) % 25);
	// final long startTime = System.currentTimeMillis();
	// client.findSuggestionsFor(query, 15,
	// new ASuggestionsRetrievedListener() {
	//
	// public void suggestionsRetrieved(
	// List<Suggestion> suggestions) {
	// float time =
	// (System.currentTimeMillis() - startTime);
	// times[queryID] = time;
	// }
	// });
	// }
	//
	// while (times[numberOfQueries - 1] == 0.0) {
	// try {
	// Thread.sleep(1);
	// } catch (InterruptedException e) {
	// e.printStackTrace();
	// }
	// }
	// float time =
	// (System.currentTimeMillis() - totalTimeStart - randomTime)
	// * 1000 / (float) numberOfQueries;
	// System.out.println("Average per query time: " + time + " µs");
	// for (float f : times) {
	// System.out.println(f);
	// }
	// }

}
