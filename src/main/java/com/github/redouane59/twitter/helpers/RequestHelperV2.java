package com.github.redouane59.twitter.helpers;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.github.redouane59.twitter.TwitterClient;
import com.github.redouane59.twitter.dto.others.TweetError;
import com.github.redouane59.twitter.dto.tweet.Tweet;
import com.github.redouane59.twitter.dto.tweet.TweetV2;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

@Slf4j
@AllArgsConstructor
public class RequestHelperV2 extends AbstractRequestHelper {

  private String bearerToken;
  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";

  public <T> Optional<T> getRequest(String url, Class<T> classType) {
    return this.getRequestWithParameters(url, null, classType);
  }

  public <T> Optional<T> getRequestWithParameters(String url, Map<String, String> parameters, Class<T> classType) {
    T result = null;
    try {
      HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
      if (parameters != null) {
        for (Map.Entry<String, String> param : parameters.entrySet()) {
          httpBuilder.addQueryParameter(param.getKey(), param.getValue());
        }
      }
      Request request = new Request.Builder().url(httpBuilder.build()).get()
          .headers(Headers.of(AUTHORIZATION, BEARER + bearerToken)).build();
      OkHttpClient client = this.getHttpClient(httpBuilder.build().url().toString());
      Response response = client.newCall(request).execute();
      String stringResponse = response.body().string();
      if (response.code() == 429) {
        this.wait(stringResponse, url);
        return this.getRequestWithParameters(url, parameters, classType);
      } else if (response.code() == 401) {
        LOGGER.info("Error 401, user may be private");
        return Optional.empty();
      } else if (response.code() < 200 || response.code() > 299) {
        logApiError("POST", url, stringResponse, response.code());
      }
      result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return Optional.ofNullable(result);
  }

  public void getAsyncRequest(String url, Consumer<Tweet> consumer, Consumer<TweetError> errorConsumer) {
    HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
    TweetStreamConsumer stream = new TweetStreamConsumer();

    Request request = new Request.Builder().url(httpBuilder.build()).get()
        .headers(Headers.of(AUTHORIZATION, BEARER + bearerToken)).build();

    Call call = new OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).connectTimeout(60, TimeUnit.SECONDS)
        .build().newCall(request);

    call.enqueue(new Callback() {
      @Override
      public void onFailure(final Call call, IOException e) {
        LOGGER.error(e.getMessage(), e);
      }

      @Override
      public void onResponse(Call call, final Response response) throws IOException {
        String tmp ="";
          while (!response.body().source().exhausted()) {

            // Read the data as soon as they arrive.
            // see https://developer.twitter.com/en/docs/tutorials/consuming-streaming-data
            try( Buffer buffer = response.body().source().getBuffer() ) {
              tmp = new String(buffer.readByteArray());
            }
            
            // Consumes data as part of a stream
            if (stream.consume(tmp)) {
              // Get all the json tweets and propagate
              String[] tweets = stream.getJsonTweets();
              for (String jsonTweet : tweets) {
                if (response.code() >= 400) {
                  TweetError error = TwitterClient.OBJECT_MAPPER.readValue(jsonTweet, TweetError.class);
                  // Catch potential issue in the consumer
                  try { errorConsumer.accept(error); } catch(Exception e) { }
                } else {
                  TweetV2 tweet = TwitterClient.OBJECT_MAPPER.readValue(jsonTweet, TweetV2.class);
                  // Catch potential issue in the consumer
                  try { consumer.accept(tweet); } catch(Exception e) { }
                }
              }
            }
          }
       
      }
    });
  }

  public <T> Optional<T> postRequest(String url, String body, Class<T> classType) {
    T result = null;
    try {
      Request request = new Request.Builder().url(url)
          .method("POST", RequestBody.create(MediaType.parse("application/json"), body))
          .headers(Headers.of(AUTHORIZATION, BEARER + bearerToken)).build();
      Response response = new OkHttpClient.Builder().build().newCall(request).execute();
      String stringResponse = response.body().string();
      if (response.code() < 200 || response.code() > 299) {
        logApiError("POST", url, stringResponse, response.code());
      }
      result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return Optional.ofNullable(result);
  }

  public static <T> Optional<T> postRequestWithHeader(String url, Map<String, String> headersMap, String body,
      Class<T> classType) {
    T result = null;
    try {
      Request request = new Request.Builder().url(url)
          .method("POST", RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), body))
          .headers(Headers.of(headersMap)).build();
      OkHttpClient client = new OkHttpClient.Builder().build();
      Response response = client.newCall(request).execute();
      String stringResponse = response.body().string();
      if (response.code() < 200 || response.code() > 299) {
        logApiError("POST", url, stringResponse, response.code());
      }
      result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
      client.connectionPool().evictAll();
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return Optional.ofNullable(result);
  }

  public static <T> Optional<T> getRequestWithHeader(String url, Map<String, String> headersMap, Class<T> classType) {
    T result = null;
    try {
      Request request = new Request.Builder().url(url).get().headers(Headers.of(headersMap)).build();
      OkHttpClient client = new OkHttpClient.Builder().build();
      Response response = client.newCall(request).execute();
      String stringResponse = response.body().string();
      if (response.code() < 200 || response.code() > 299) {
        logApiError("POST", url, stringResponse, response.code());
      }
      result = TwitterClient.OBJECT_MAPPER.readValue(stringResponse, classType);
      client.connectionPool().evictAll();
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return Optional.ofNullable(result);
  }
}