package de.completionary.server.thrift;

import java.util.List;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

import de.completionary.proxy.elasticsearch.SuggestionIndex;
import de.completionary.proxy.thrift.services.Suggestion;
import de.completionary.proxy.thrift.services.SuggestionService;

public class SuggestionHandler implements SuggestionService.AsyncIface {

    public void findSuggestionsFor(
            String index,
            String query,
            short k,
            final AsyncMethodCallback resultHandler) throws TException {

        SuggestionIndex.getIndex(index).async_findSuggestionsFor(query, k,
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

    public static class SuggestionHandlerSync implements
            SuggestionService.Iface {

        public List<Suggestion> findSuggestionsFor(
                String index,
                String query,
                short k) throws TException {
            return SuggestionIndex.getIndex(index).findSuggestionsFor(query, k);
        }

    }

}
