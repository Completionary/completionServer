package de.completionary.server.thrift;

import java.util.List;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

import de.completionary.proxy.elasticsearch.SuggestionIndex;
import de.completionary.proxy.thrift.services.suggestion.AnalyticsData;
import de.completionary.proxy.thrift.services.suggestion.Suggestion;
import de.completionary.proxy.thrift.services.suggestion.SuggestionService;

public class SuggestionHandler implements SuggestionService.AsyncIface {

	public void findSuggestionsFor(String index, String query,
			short numberOfSuggestions, AnalyticsData userData,
			final AsyncMethodCallback resultHandler) throws TException {

		SuggestionIndex.getIndex(index).async_findSuggestionsFor(query,
				numberOfSuggestions, userData,
				new AsyncMethodCallback<List<Suggestion>>() {

					@Override
					public void onComplete(List<Suggestion> result) {
						resultHandler.onComplete(result);
					}

					@Override
					public void onError(Exception e) {
						resultHandler.onError(e);
					}
				});
	}

	@Override
	public void onSearchSessionFinished(String index, AnalyticsData userData,
			AsyncMethodCallback resultHandler) throws TException {
		SuggestionIndex.getIndex(index).onSearchSessionFinished(userData);
		resultHandler.onComplete(null);
	}

	@Override
	public void onSuggestionSelected(String index, String suggestionID,
			String suggestionString, AnalyticsData userData, AsyncMethodCallback resultHandler)
			throws TException {
		SuggestionIndex.getIndex(index).onSuggestionSelected(suggestionID,
				suggestionString, userData);
		resultHandler.onComplete(null);
	}

	public static class SuggestionHandlerSync implements
			SuggestionService.Iface {

		public List<Suggestion> findSuggestionsFor(String index, String query,
				short numberOfSuggestions, AnalyticsData userData)
				throws TException {
			return SuggestionIndex.getIndex(index).findSuggestionsFor(query,
					numberOfSuggestions, userData);
		}

		@Override
		public void onSearchSessionFinished(String index, AnalyticsData userData)
				throws TException {
			SuggestionIndex.getIndex(index).onSearchSessionFinished(userData);
		}

		@Override
		public void onSuggestionSelected(String index, String suggestionID,
				String suggestionString, AnalyticsData userData) throws TException {
			SuggestionIndex.getIndex(index).onSuggestionSelected(suggestionID,
					suggestionString, userData);
		}
	}
}
