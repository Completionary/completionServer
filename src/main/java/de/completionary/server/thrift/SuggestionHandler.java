package de.completionary.server.thrift;

import java.util.ArrayList;
import java.util.List;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

import de.completionary.proxy.elasticsearch.SuggestionIndex;
import de.completionary.proxy.server.ISuggestionsRetrievedListener;
import de.completionary.proxy.thrift.services.Suggestion;
import de.completionary.proxy.thrift.services.SuggestionService;



public class SuggestionHandler implements SuggestionService.AsyncIface {

    public void findSuggestionsFor(
            String index,
            String query,
            short k,
            final AsyncMethodCallback resultHandler) throws TException {

                SuggestionIndex.getIndex(index).findSuggestionsFor(query, k,
                        new ISuggestionsRetrievedListener() {
        
                            public void suggestionsRetrieved(
                                    List<Suggestion> suggestions) {
                                resultHandler.onComplete(suggestions);
                            }
                        });
    }

    public static class SuggestionHandlerSync implements
            SuggestionService.Iface {

        public List<Suggestion> findSuggestionsFor(
                String index,
                String query,
                short k) throws TException {
            List<Suggestion> suggestions = new ArrayList<Suggestion>();
            suggestions.add(new Suggestion("adsf", "payload"));
            return suggestions;
        }

    }
}
